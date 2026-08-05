package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign.CampaignResultDraftContext;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultRequest;
import com.mgmtp.gives.dto.campaign.CampaignResultResponse;
import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.dto.campaign.DonorThankYouContext;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingListResponse;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CampaignTaskRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.specification.CampaignTaskSpecifications;
import com.mgmtp.gives.service.CampaignMemberService;
import com.mgmtp.gives.service.CampaignResultService;
import com.mgmtp.gives.service.CampaignSpendingService;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.GeminiService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.service.NotificationService;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.mgmtp.gives.enums.DonationStatus;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignResultServiceImpl implements CampaignResultService {

    private static final String RESULT_EMAIL_SUBJECT_PREFIX = "Campaign Results: ";

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMemberService campaignMemberService;
    private final DonationRepository donationRepository;
    private final AnnouncementRepository announcementRepository;
    private final GeminiService geminiService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final TemplateEngine templateEngine;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMediaMapper campaignMediaMapper;
    private final CampaignTaskRepository campaignTaskRepository;
    private final MediaService mediaService;
    private final MailProps mailProps;
    private final CampaignSpendingService campaignSpendingService;
    private final CampaignResultDraftContextFactory draftContextFactory;
    private final CampaignResultPdfRenderer pdfRenderer;

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
                    sendResultNotifications(campaign);
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

    private void sendResultNotifications(Campaign campaign) {
        String campaignName = campaign.getTitle() != null ? campaign.getTitle() : "this campaign";
        String linkUrl = "/campaigns/" + campaign.getId() + "/result";
        String reportLink = buildReportLink(campaign.getId());
        byte[] reportPdf = tryRenderResultPdf(campaign);
        String reportPdfFilename = "final-report-" + campaign.getId() + ".pdf";

        Map<Long, ResultRecipient> recipients = collectResultRecipients(campaign);
        boolean hasDonors = recipients.values().stream().anyMatch(r -> r.donor);
        Map<Long, String> aiThankYouMessages = hasDonors ? generateDonorThankYouMessages(campaign) : Map.of();

        for (ResultRecipient recipient : recipients.values()) {
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(Set.of(new NotificationRecipient(recipient.userId, recipient.email)))
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message(buildResultNotificationMessage(campaignName, recipient))
                    .linkUrl(linkUrl)
                    .build());

            String subject = RESULT_EMAIL_SUBJECT_PREFIX + campaignName;
            String body = buildResultEmailBody(recipient, campaignName,
                    aiThankYouMessages.get(recipient.userId),
                    recipient.receivesReport() ? reportLink : null);
            if (reportPdf != null && recipient.receivesReport()) {
                emailService.sendHtmlEmailWithAttachment(
                        recipient.email, subject, body, reportPdf, reportPdfFilename, "application/pdf");
            } else {
                emailService.sendHtmlEmail(recipient.email, subject, body);
            }
        }
    }

    /**
     * Merged view of one user's roles in a campaign. A user can be a follower, volunteer and
     * donor at once (donating or volunteering auto-follows the campaign), so recipients are
     * merged by user id to guarantee exactly one email and one notification per user.
     */
    private static final class ResultRecipient {
        private final Long userId;
        private final String email;
        private final String fullName;
        private boolean follower;
        private boolean volunteer;
        private boolean donor;
        private String formattedDonationAmount;

        private ResultRecipient(Long userId, String email, String fullName) {
            this.userId = userId;
            this.email = email;
            this.fullName = fullName;
        }

        /** Following is implied for volunteers and donors, so it is only thanked on its own. */
        private boolean isFollowerOnly() {
            return follower && !volunteer && !donor;
        }

        private boolean receivesReport() {
            return volunteer || donor;
        }
    }

    private Map<Long, ResultRecipient> collectResultRecipients(Campaign campaign) {
        Map<Long, ResultRecipient> recipients = new LinkedHashMap<>();
        for (User user : campaignFollowerRepository.findFollowerUsersByCampaignId(campaign.getId())) {
            recipientFor(recipients, user.getId(), user.getEmail(), user.getFullName()).follower = true;
        }
        for (User user : campaignMemberRepository.findUsersByCampaignIdAndRole(
                campaign.getId(), CampaignMemberRole.VOLUNTEER)) {
            recipientFor(recipients, user.getId(), user.getEmail(), user.getFullName()).volunteer = true;
        }
        for (DonorNotificationInfo donor : donationRepository.findDonorNotificationInfoByCampaignId(
                campaign.getId(), DonationStatus.SUCCESSFUL)) {
            ResultRecipient recipient = recipientFor(recipients, donor.userId(), donor.email(), donor.fullName());
            recipient.donor = true;
            // totalAmount is null for donors who only gave goods (GOODS donations carry no amount)
            recipient.formattedDonationAmount = donor.totalAmount() != null && donor.totalAmount() > 0
                    ? NumberFormat.getNumberInstance(Locale.US).format(donor.totalAmount())
                    : null;
        }
        return recipients;
    }

    private static ResultRecipient recipientFor(
            Map<Long, ResultRecipient> recipients, Long userId, String email, String fullName) {
        return recipients.computeIfAbsent(userId, id -> new ResultRecipient(id, email, fullName));
    }

    private String buildResultNotificationMessage(String campaignName, ResultRecipient recipient) {
        StringBuilder message = new StringBuilder(
                "The final results for \"" + campaignName + "\" are now available.");
        if (recipient.volunteer) {
            message.append(" Thank you for your dedication and hard work — your contribution made a real difference!");
        }
        if (recipient.donor) {
            message.append(' ').append(recipient.formattedDonationAmount != null
                    ? "Thank you for your generous donation of " + recipient.formattedDonationAmount
                            + " VND — together, we made it happen!"
                    : "Thank you for your generous contribution — together, we made it happen!");
        }
        if (recipient.isFollowerOnly()) {
            message.append(" Thank you for following and supporting this campaign — your interest truly mattered to us!");
        }
        return message.toString();
    }

    /**
     * Builds per-donor contribution data from the database and asks the AI for personalized
     * thank-you messages. Returns an empty map on any failure so emails fall back to the
     * static template.
     */
    private Map<Long, String> generateDonorThankYouMessages(Campaign campaign) {
        if (!geminiService.isConfigured()) {
            return Map.of();
        }
        try {
            List<Donation> donations =
                    donationRepository.findByCampaignIdAndStatus(campaign.getId(), DonationStatus.SUCCESSFUL);
            Map<Long, List<Donation>> byUser = donations.stream()
                    .filter(d -> d.getUser() != null)
                    .collect(Collectors.groupingBy(d -> d.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

            List<DonorThankYouContext> contexts = new ArrayList<>();
            for (Map.Entry<Long, List<Donation>> entry : byUser.entrySet()) {
                List<Donation> userDonations = entry.getValue();
                long totalMoney = userDonations.stream()
                        .filter(d -> d.getType() == DonationType.MONEY && d.getAmount() != null)
                        .mapToLong(Donation::getAmount)
                        .sum();
                List<String> goodsItems = userDonations.stream()
                        .filter(d -> d.getType() == DonationType.GOODS)
                        .map(CampaignResultDraftContextFactory::describeGoods)
                        .filter(s -> !s.isBlank())
                        .toList();
                contexts.add(new DonorThankYouContext(
                        entry.getKey(),
                        userDonations.get(0).getUser().getFullName(),
                        totalMoney,
                        userDonations.size(),
                        goodsItems));
            }

            return geminiService.generateDonorThankYouMessages(campaign, contexts);
        } catch (Exception e) {
            log.warn("Falling back to static donor emails, AI generation failed: campaignId={}, error={}",
                    campaign.getId(), e.getMessage());
            return Map.of();
        }
    }

    private String buildResultEmailBody(ResultRecipient recipient, String campaignName, String aiMessage,
            String reportLink) {
        Context context = new Context();

        context.setVariable("fullName", recipient.fullName);
        context.setVariable("campaignName", campaignName);
        context.setVariable("isVolunteer", recipient.volunteer);
        context.setVariable("isDonor", recipient.donor);
        context.setVariable("isFollowerOnly", recipient.isFollowerOnly());
        context.setVariable("formattedAmount", recipient.formattedDonationAmount);
        context.setVariable("aiMessage", aiMessage);
        context.setVariable("reportLink", reportLink);

        return templateEngine.process("campaign-result-notification", context);
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

    /**
     * Swallows rendering failures, returning null instead
     * of throwing - used when sending result notification emails so a PDF rendering bug never
     * blocks the emails themselves from going out.
     */
    private byte[] tryRenderResultPdf(Campaign campaign) {
        try {
            return pdfRenderer.render(campaign);
        } catch (Exception e) {
            log.error("Failed to render final report PDF for notification email: campaignId={}", campaign.getId(), e);
            return null;
        }
    }

    private String buildReportLink(Long campaignId) {
        return UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .pathSegment("campaigns", campaignId.toString(), "result")
                .toUriString();
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
