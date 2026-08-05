package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignResultDraftContext;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultRequest;
import com.mgmtp.gives.dto.campaign.CampaignResultResponse;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingListResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CampaignTaskRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.notification.publisher.CampaignResultNotificationPublisher;
import com.mgmtp.gives.specification.CampaignTaskSpecifications;
import com.mgmtp.gives.service.CampaignMemberService;
import com.mgmtp.gives.service.CampaignResultService;
import com.mgmtp.gives.service.CampaignSpendingService;
import com.mgmtp.gives.service.GeminiService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.service.support.CampaignResultDraftContextFactory;
import com.mgmtp.gives.service.support.CampaignResultMetrics;
import com.mgmtp.gives.service.support.CampaignResultPdfRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mgmtp.gives.enums.DonationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignResultServiceImpl implements CampaignResultService {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMemberService campaignMemberService;
    private final DonationRepository donationRepository;
    private final AnnouncementRepository announcementRepository;
    private final GeminiService geminiService;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMediaMapper campaignMediaMapper;
    private final CampaignTaskRepository campaignTaskRepository;
    private final MediaService mediaService;
    private final CampaignSpendingService campaignSpendingService;
    private final CampaignResultDraftContextFactory draftContextFactory;
    private final CampaignResultPdfRenderer pdfRenderer;
    private final CampaignResultNotificationPublisher resultNotificationPublisher;

    @Override
    @Transactional
    public CampaignResultResponse postResult(Long campaignId, CampaignResultRequest request, User currentUser) {
        Campaign campaign = findAndValidateCampaign(campaignId);
        checkAuthorized(campaign, currentUser);

        if (campaign.isResultPosted()) {
            log.warn("Attempt to re-post result for already-posted campaign: campaignId={}", campaignId);
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_ALREADY_POSTED);
        }

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        long finalAmount = request.finalAmountRaised() != null ? request.finalAmountRaised() : confirmedTotal;

        long donorCount = donationRepository.countDistinctDonorsByCampaignId(campaignId);
        long volunteerCount = campaignMemberRepository.countByCampaignIdAndRoleInCampaign(
                campaignId, CampaignMemberRole.VOLUNTEER);

        campaign.setResultSummary(request.resultSummary());
        campaign.setFinalAmountRaised(finalAmount);
        campaign.setItemsSummary(request.itemsSummary());
        campaign.setAcknowledgements(request.acknowledgements());
        campaign.setTaskSummary(request.taskSummary());
        campaign.setResultPosted(true);
        campaign.setResultPublishedBy(currentUser);
        campaign.setResultPublishedAt(LocalDateTime.now());
        campaign.setFinalDonorCount(donorCount);
        campaign.setFinalVolunteerCount(volunteerCount);
        campaignRepository.save(campaign);
        List<CampaignMedia> resultMedia = saveResultMedia(campaign, request.mediaIds());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    resultNotificationPublisher.publish(campaign);
                } catch (Exception ex) {
                    log.error("Failed to send result notifications for campaignId={}: {}", campaignId, ex.getMessage(), ex);
                }
            }
        });

        log.info("Campaign result posted: campaignId={}, userId={}", campaignId, currentUser.getId());
        return buildResponse(campaign, confirmedTotal, resultMedia);
    }

    @Override
    @Transactional
    public CampaignResultResponse updateResult(Long campaignId, CampaignResultRequest request, User currentUser) {
        Campaign campaign = findAndValidateCampaign(campaignId);
        checkAuthorized(campaign, currentUser);

        if (!campaign.isResultPosted()) {
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_NOT_FOUND);
        }

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        long finalAmount = request.finalAmountRaised() != null ? request.finalAmountRaised() : confirmedTotal;

        campaign.setResultSummary(request.resultSummary());
        campaign.setFinalAmountRaised(finalAmount);
        campaign.setItemsSummary(request.itemsSummary());
        campaign.setAcknowledgements(request.acknowledgements());
        campaign.setTaskSummary(request.taskSummary());
        campaign.setResultPublishedBy(currentUser);
        campaign.setResultPublishedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
        List<CampaignMedia> resultMedia = saveResultMedia(campaign, request.mediaIds());

        log.info("Campaign result updated: campaignId={}, userId={}", campaignId, currentUser.getId());
        return buildResponse(campaign, confirmedTotal, resultMedia);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResultResponse getResult(Long campaignId) {
        log.info("Fetching campaign result: campaignId={}", campaignId);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (!campaign.isResultPosted()) {
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_NOT_FOUND);
        }

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        return buildResponse(campaign, confirmedTotal);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResultGenerateResponse generateResultDraft(Long campaignId, User currentUser) {
        log.info("Generating AI result draft: campaignId={}, userId={}", campaignId, currentUser.getId());
        Campaign campaign = findAndValidateCampaign(campaignId);
        checkAuthorized(campaign, currentUser);

        long confirmedTotal = donationRepository.sumConfirmedAmountByCampaignId(campaignId);
        long donorCount = donationRepository.countDistinctDonorsByCampaignId(campaignId);
        long volunteerCount = campaignMemberRepository.countByCampaignIdAndRoleInCampaign(
                campaignId, CampaignMemberRole.VOLUNTEER);

        List<Donation> donations = donationRepository.findByCampaignIdAndStatus(campaignId, DonationStatus.SUCCESSFUL);
        var announcements = announcementRepository.findByCampaignIdOrderByPublishedAtAsc(campaignId);
        List<CampaignTask> tasks = campaignTaskRepository.findActiveTasksWithAssignments(campaignId);

        CampaignResultDraftContext draftContext = draftContextFactory.create(
                campaign,
                confirmedTotal,
                donorCount,
                volunteerCount,
                donations,
                announcements,
                tasks);
        return geminiService.generateCampaignResultDraft(campaign, draftContext);
    }

    /**
     * Re-tags campaign media as belonging to the final report (context=FINAL_REPORT) so it's
     * excluded from the general campaign gallery. Media dropped from the report reverts to
     * context=CAMPAIGN. Media already attached to an announcement or meeting (via FK) is
     * rejected rather than silently stolen. Returns the final FINAL_REPORT-tagged media in the
     * order the caller requested, so buildResponse can reuse it without re-querying.
     */
    private List<CampaignMedia> saveResultMedia(Campaign campaign, List<Long> mediaIds) {
        List<CampaignMedia> currentMedia = campaignMediaRepository
                .findByCampaignIdAndContextAndDeletedAtIsNull(campaign.getId(), MediaContext.FINAL_REPORT);

        return mediaService.reconcileMediaTags(campaign.getId(), currentMedia, mediaIds,
                media -> media.setContext(MediaContext.CAMPAIGN),
                (media, index) -> {
                    if (media.getAnnouncement() != null || media.getMeeting() != null) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Media with ID " + media.getId() + " is already attached to another feature");
                    }
                    if (media.isCover()) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Campaign cover photo cannot be attached to the final report");
                    }
                    media.setContext(MediaContext.FINAL_REPORT);
                });
    }

    private Campaign findAndValidateCampaign(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (campaign.getStatus() != CampaignStatus.COMPLETED) {
            log.warn("Campaign {} is not completed, status={}", campaignId, campaign.getStatus());
            throw new AppException(ErrorCode.CAMPAIGN_NOT_COMPLETED);
        }
        return campaign;
    }

    private void checkAuthorized(Campaign campaign, User currentUser) {
        if (!campaignMemberService.canManageCampaign(campaign.getId(), currentUser)) {
            log.warn("Unauthorized result access: campaignId={}, userId={}", campaign.getId(), currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_RESULT_ACCESS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateResultPdf(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.isResultPosted()) {
            throw new AppException(ErrorCode.CAMPAIGN_RESULT_NOT_FOUND);
        }
        return pdfRenderer.render(campaign);
    }

    private CampaignResultResponse buildResponse(Campaign campaign, long confirmedTotal) {
        List<CampaignMedia> resultMedia = campaignMediaRepository
                .findByCampaignIdAndContextAndDeletedAtIsNull(campaign.getId(), MediaContext.FINAL_REPORT);
        return buildResponse(campaign, confirmedTotal, resultMedia);
    }

    private CampaignResultResponse buildResponse(Campaign campaign, long confirmedTotal, List<CampaignMedia> resultMedia) {
        long donorCount = campaign.getFinalDonorCount() != null
                ? campaign.getFinalDonorCount()
                : donationRepository.countDistinctDonorsByCampaignId(campaign.getId());
        long volunteerCount = campaign.getFinalVolunteerCount() != null
                ? campaign.getFinalVolunteerCount()
                : campaignMemberRepository.countByCampaignIdAndRoleInCampaign(campaign.getId(), CampaignMemberRole.VOLUNTEER);

        long amountForGoal = campaign.getFinalAmountRaised() != null ? campaign.getFinalAmountRaised() : confirmedTotal;
        double goalPercent = CampaignResultMetrics.calculateGoalPercent(campaign.getTarget(), amountForGoal);

        TaskCounts taskCounts = computeActiveTaskCounts(campaign.getId());

        List<CampaignMediaResponse> mediaResponses = campaignMediaMapper.toResponseList(resultMedia);

        CampaignSpendingListResponse spending =
                campaignSpendingService.getSpendingsByCampaign(campaign.getId(), confirmedTotal);

        return CampaignResultResponse.builder()
                .campaignId(campaign.getId())
                .resultSummary(campaign.getResultSummary())
                .finalAmountRaised(campaign.getFinalAmountRaised())
                .itemsSummary(campaign.getItemsSummary())
                .acknowledgements(campaign.getAcknowledgements())
                .taskSummary(campaign.getTaskSummary())
                .publishedByName(campaign.getResultPublishedBy() != null
                        ? campaign.getResultPublishedBy().getFullName() : null)
                .publishedAt(campaign.getResultPublishedAt())
                .media(mediaResponses)
                .totalRaised(confirmedTotal)
                .donorCount(donorCount)
                .volunteerCount(volunteerCount)
                .goalPercent(goalPercent)
                .taskCount(taskCounts.total())
                .completedTaskCount(taskCounts.completed())
                .spendingItems(spending.items())
                .totalSpent(spending.totalSpent())
                .remainingFunds(spending.remainingFunds())
                .build();
    }

    private record TaskCounts(long total, long completed) {}

    private TaskCounts computeActiveTaskCounts(Long campaignId) {
        Specification<CampaignTask> activeTaskSpec = CampaignTaskSpecifications.hasCampaignId(campaignId)
                .and(CampaignTaskSpecifications.isNotDeleted())
                .and(CampaignTaskSpecifications.hasIsArchived(false));
        long total = campaignTaskRepository.count(activeTaskSpec);
        long completed = campaignTaskRepository
                .count(activeTaskSpec.and(CampaignTaskSpecifications.hasStatus(TaskStatus.DONE)));
        return new TaskCounts(total, completed);
    }
}
