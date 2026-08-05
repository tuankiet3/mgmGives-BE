package com.mgmtp.gives.notification.publisher;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.dto.campaign.DonorThankYouContext;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.GeminiService;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.service.support.CampaignResultDraftContextFactory;
import com.mgmtp.gives.service.support.CampaignResultPdfRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignResultNotificationPublisher {

    private static final String RESULT_EMAIL_SUBJECT_PREFIX = "Campaign Results: ";

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final DonationRepository donationRepository;
    private final GeminiService geminiService;
    private final TemplateEngine templateEngine;
    private final MailProps mailProps;
    private final CampaignResultPdfRenderer pdfRenderer;

    public void publish(Campaign campaign) {
        String campaignName = campaign.getTitle() != null ? campaign.getTitle() : "this campaign";
        String linkUrl = "/campaigns/" + campaign.getId() + "/result";
        String reportLink = buildReportLink(campaign.getId());
        byte[] reportPdf = tryRenderResultPdf(campaign);
        String reportPdfFilename = "final-report-" + campaign.getId() + ".pdf";

        Map<Long, ResultRecipient> recipients = collectRecipients(campaign);
        boolean hasDonors = recipients.values().stream().anyMatch(ResultRecipient::isDonor);
        Map<Long, String> aiThankYouMessages = hasDonors ? generateDonorThankYouMessages(campaign) : Map.of();

        for (ResultRecipient recipient : recipients.values()) {
            notificationService.createNotification(CreateNotificationCommand.builder()
                    .recipients(Set.of(new NotificationRecipient(recipient.userId(), recipient.email())))
                    .type(NotificationType.CAMPAIGN_RESULT_POSTED)
                    .title("Campaign Results Published")
                    .message(buildNotificationMessage(campaignName, recipient))
                    .linkUrl(linkUrl)
                    .build());

            String subject = RESULT_EMAIL_SUBJECT_PREFIX + campaignName;
            String body = buildEmailBody(
                    recipient,
                    campaignName,
                    aiThankYouMessages.get(recipient.userId()),
                    recipient.receivesReport() ? reportLink : null);
            if (reportPdf != null && recipient.receivesReport()) {
                emailService.sendHtmlEmailWithAttachment(
                        recipient.email(), subject, body, reportPdf, reportPdfFilename, "application/pdf");
            } else {
                emailService.sendHtmlEmail(recipient.email(), subject, body);
            }
        }
    }

    /**
     * A user can follow, volunteer, and donate at once. Merging roles by user id guarantees one
     * in-app notification and one email while retaining every applicable thank-you message.
     */
    private Map<Long, ResultRecipient> collectRecipients(Campaign campaign) {
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

    private static String buildNotificationMessage(String campaignName, ResultRecipient recipient) {
        StringBuilder message = new StringBuilder(
                "The final results for \"" + campaignName + "\" are now available.");
        if (recipient.isVolunteer()) {
            message.append(" Thank you for your dedication and hard work — your contribution made a real difference!");
        }
        if (recipient.isDonor()) {
            message.append(' ').append(recipient.formattedDonationAmount() != null
                    ? "Thank you for your generous donation of " + recipient.formattedDonationAmount()
                            + " VND — together, we made it happen!"
                    : "Thank you for your generous contribution — together, we made it happen!");
        }
        if (recipient.isFollowerOnly()) {
            message.append(" Thank you for following and supporting this campaign — your interest truly mattered to us!");
        }
        return message.toString();
    }

    /** Returns an empty map on AI or persistence failure so static email content remains usable. */
    private Map<Long, String> generateDonorThankYouMessages(Campaign campaign) {
        if (!geminiService.isConfigured()) {
            return Map.of();
        }
        try {
            List<Donation> donations =
                    donationRepository.findByCampaignIdAndStatus(campaign.getId(), DonationStatus.SUCCESSFUL);
            Map<Long, List<Donation>> byUser = donations.stream()
                    .filter(donation -> donation.getUser() != null)
                    .collect(Collectors.groupingBy(
                            donation -> donation.getUser().getId(),
                            LinkedHashMap::new,
                            Collectors.toList()));

            List<DonorThankYouContext> contexts = new ArrayList<>();
            for (Map.Entry<Long, List<Donation>> entry : byUser.entrySet()) {
                List<Donation> userDonations = entry.getValue();
                long totalMoney = userDonations.stream()
                        .filter(donation -> donation.getType() == DonationType.MONEY && donation.getAmount() != null)
                        .mapToLong(Donation::getAmount)
                        .sum();
                List<String> goodsItems = userDonations.stream()
                        .filter(donation -> donation.getType() == DonationType.GOODS)
                        .map(CampaignResultDraftContextFactory::describeGoods)
                        .filter(description -> !description.isBlank())
                        .toList();
                contexts.add(new DonorThankYouContext(
                        entry.getKey(),
                        userDonations.getFirst().getUser().getFullName(),
                        totalMoney,
                        userDonations.size(),
                        goodsItems));
            }

            return geminiService.generateDonorThankYouMessages(campaign, contexts);
        } catch (Exception exception) {
            log.warn(
                    "Falling back to static donor emails, AI generation failed: campaignId={}, error={}",
                    campaign.getId(),
                    exception.getMessage());
            return Map.of();
        }
    }

    private String buildEmailBody(
            ResultRecipient recipient, String campaignName, String aiMessage, String reportLink) {
        Context context = new Context();
        context.setVariable("fullName", recipient.fullName());
        context.setVariable("campaignName", campaignName);
        context.setVariable("isVolunteer", recipient.isVolunteer());
        context.setVariable("isDonor", recipient.isDonor());
        context.setVariable("isFollowerOnly", recipient.isFollowerOnly());
        context.setVariable("formattedAmount", recipient.formattedDonationAmount());
        context.setVariable("aiMessage", aiMessage);
        context.setVariable("reportLink", reportLink);
        return templateEngine.process("campaign-result-notification", context);
    }

    private byte[] tryRenderResultPdf(Campaign campaign) {
        try {
            return pdfRenderer.render(campaign);
        } catch (Exception exception) {
            log.error(
                    "Failed to render final report PDF for notification email: campaignId={}",
                    campaign.getId(),
                    exception);
            return null;
        }
    }

    private String buildReportLink(Long campaignId) {
        return UriComponentsBuilder.fromUriString(mailProps.getFrontendUrl())
                .pathSegment("campaigns", campaignId.toString(), "result")
                .toUriString();
    }

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

        private Long userId() {
            return userId;
        }

        private String email() {
            return email;
        }

        private String fullName() {
            return fullName;
        }

        private boolean isFollowerOnly() {
            return follower && !volunteer && !donor;
        }

        private boolean receivesReport() {
            return volunteer || donor;
        }

        private boolean isVolunteer() {
            return volunteer;
        }

        private boolean isDonor() {
            return donor;
        }

        private String formattedDonationAmount() {
            return formattedDonationAmount;
        }
    }
}
