package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultRequest;
import com.mgmtp.gives.dto.campaign.CampaignResultResponse;
import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.CampaignResultService;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.GeminiService;
import com.mgmtp.gives.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.mgmtp.gives.enums.DonationStatus;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignResultServiceImpl implements CampaignResultService {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final DonationRepository donationRepository;
    private final CampaignMediaMapper campaignMediaMapper;
    private final GeminiService geminiService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final TemplateEngine templateEngine;

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
        campaign.setResultPosted(true);
        campaign.setResultPublishedBy(currentUser);
        campaign.setResultPublishedAt(LocalDateTime.now());
        campaign.setFinalDonorCount(donorCount);
        campaign.setFinalVolunteerCount(volunteerCount);
        campaignRepository.save(campaign);
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
        return buildResponse(campaign, confirmedTotal);
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
        campaign.setResultPublishedBy(currentUser);
        campaign.setResultPublishedAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        log.info("Campaign result updated: campaignId={}, userId={}", campaignId, currentUser.getId());
        return buildResponse(campaign, confirmedTotal);
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
        double goalPercent = campaign.getTarget() != null && campaign.getTarget() > 0
                ? Math.min(100.0, (confirmedTotal * 100.0) / campaign.getTarget())
                : 0.0;

        return geminiService.generateCampaignResultDraft(campaign, confirmedTotal, donorCount, volunteerCount, goalPercent);
    }

    private void sendResultNotifications(Campaign campaign) {
        String campaignName = campaign.getTitle() != null ? campaign.getTitle() : "this campaign";
        String linkUrl = "/campaigns/" + campaign.getId() + "/result";

        // --- Followers (single query) ---
        List<User> followerUsers = campaignFollowerRepository.findFollowerUsersByCampaignId(campaign.getId());
        if (!followerUsers.isEmpty()) {
            Set<NotificationRecipient> followerRecipients = followerUsers.stream()
                    .map(u -> new NotificationRecipient(u.getId(), u.getEmail()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(followerRecipients)
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message("The final results for \"" + campaignName + "\" have been posted. "
                            + "Thank you for following and supporting this campaign — your interest truly mattered to us!")
                    .linkUrl(linkUrl)
                    .build());
            for (User user : followerUsers) {
                emailService.sendHtmlEmail(
                        user.getEmail(),
                        "Campaign Results: " + campaignName,
                        buildFollowerEmailBody(user.getFullName(), campaignName));
            }
        }

        // --- Volunteers (single query) ---
        List<User> volunteerUsers =
                campaignMemberRepository.findUsersByCampaignIdAndRole(campaign.getId(), CampaignMemberRole.VOLUNTEER);
        if (!volunteerUsers.isEmpty()) {
            Set<NotificationRecipient> volunteerRecipients = volunteerUsers.stream()
                    .map(u -> new NotificationRecipient(u.getId(), u.getEmail()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(volunteerRecipients)
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message("The final results for \"" + campaignName + "\" are now available. "
                            + "Thank you for your dedication and hard work — your contribution made a real difference!")
                    .linkUrl(linkUrl)
                    .build());
            for (User user : volunteerUsers) {
                emailService.sendHtmlEmail(
                        user.getEmail(),
                        "Campaign Results: " + campaignName,
                        buildVolunteerEmailBody(user.getFullName(), campaignName));
            }
        }

        // --- Donors (personalized with donation amount) ---
        List<DonorNotificationInfo> donors =
                donationRepository.findDonorNotificationInfoByCampaignId(campaign.getId(), DonationStatus.SUCCESSFUL);
        for (DonorNotificationInfo donor : donors) {
            String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(donor.totalAmount());
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(Set.of(new NotificationRecipient(donor.userId(), donor.email())))
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message("The final results for \"" + campaignName + "\" are now available. "
                            + "Thank you for your generous donation of " + formattedAmount + " VND — together, we made it happen!")
                    .linkUrl(linkUrl)
                    .build());

            emailService.sendHtmlEmail(
                    donor.email(),
                    "Campaign Results: " + campaignName,
                    buildDonorEmailBody(donor.fullName(), campaignName, formattedAmount));
        }
    }

    private String buildFollowerEmailBody(String fullName, String campaignName) {
        Context context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("campaignName", campaignName);
        return templateEngine.process("follower-result-notification", context);
    }

    private String buildVolunteerEmailBody(String fullName, String campaignName) {
        Context context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("campaignName", campaignName);
        return templateEngine.process("volunteer-result-notification", context);
    }

    private String buildDonorEmailBody(String fullName, String campaignName, String formattedAmount) {
        Context context = new Context();

        context.setVariable("fullName", fullName);
        context.setVariable("campaignName", campaignName);
        context.setVariable("formattedAmount", formattedAmount);

        return templateEngine.process(
                "donor-result-notification",
                context);
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
        if (currentUser.getRole() == UserRole.ADMIN) return;
        boolean isCampaignAdmin = campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaign.getId(), currentUser.getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
        if (!isCampaignAdmin) {
            log.warn("Unauthorized result access: campaignId={}, userId={}", campaign.getId(), currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_RESULT_ACCESS);
        }
    }

    private CampaignResultResponse buildResponse(Campaign campaign, long confirmedTotal) {
        long donorCount = campaign.getFinalDonorCount() != null
                ? campaign.getFinalDonorCount()
                : donationRepository.countDistinctDonorsByCampaignId(campaign.getId());
        long volunteerCount = campaign.getFinalVolunteerCount() != null
                ? campaign.getFinalVolunteerCount()
                : campaignMemberRepository.countByCampaignIdAndRoleInCampaign(campaign.getId(), CampaignMemberRole.VOLUNTEER);

        long amountForGoal = campaign.getFinalAmountRaised() != null ? campaign.getFinalAmountRaised() : confirmedTotal;
        double goalPercent = campaign.getTarget() != null && campaign.getTarget() > 0
                ? Math.min(100.0, (amountForGoal * 100.0) / campaign.getTarget())
                : 0.0;

        List<CampaignMedia> resultMedias = campaignMediaRepository.findByCampaignIdAndContextAndDeletedAtIsNull(
                campaign.getId(), "RESULT");

        List<CampaignMediaResponse> mediaResponses = campaignMediaMapper.toResponseList(resultMedias);

        return CampaignResultResponse.builder()
                .campaignId(campaign.getId())
                .resultSummary(campaign.getResultSummary())
                .finalAmountRaised(campaign.getFinalAmountRaised())
                .itemsSummary(campaign.getItemsSummary())
                .acknowledgements(campaign.getAcknowledgements())
                .publishedByName(campaign.getResultPublishedBy() != null
                        ? campaign.getResultPublishedBy().getFullName() : null)
                .publishedAt(campaign.getResultPublishedAt())
                .totalRaised(confirmedTotal)
                .donorCount(donorCount)
                .volunteerCount(volunteerCount)
                .goalPercent(goalPercent)
                .resultMedias(mediaResponses)
                .build();
    }
}
