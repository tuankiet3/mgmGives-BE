package com.mgmtp.gives.notification.publisher;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.event.notification.CampaignDonationConfirmedEvent;
import com.mgmtp.gives.event.notification.DonationConfirmedEvent;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DonationNotificationPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final CampaignMemberRepository campaignMemberRepository;

    public void publishDonationConfirmedEvents(Donation donation) {
        publishDonorDonationConfirmedEvent(donation);
        publishCampaignDonationConfirmedEvent(donation);
    }

    public void publishDonorDonationConfirmedEvent(Donation donation) {
        eventPublisher.publishEvent(
                new DonationConfirmedEvent(
                        donation.getId(),
                        donation.getCampaign().getId(),
                        donation.getCampaign().getTitle(),
                        donation.getUser().getId(),
                        donation.getType(),
                        donation.getAmount(),
                        donation.getDetail()));
    }

    public void publishCampaignDonationConfirmedEvent(Donation donation) {
        eventPublisher.publishEvent(
                new CampaignDonationConfirmedEvent(
                        donation.getId(),
                        donation.getCampaign().getId(),
                        donation.getCampaign().getTitle(),
                        donation.getUser() != null ? donation.getUser().getId() : null,
                        getDonorName(donation),
                        donation.isAnonymous(),
                        donation.getType(),
                        donation.getAmount(),
                        donation.getDetail(),
                        donation.getConfirmedBy() != null ? donation.getConfirmedBy().getId() : null));
    }

    public void publishPendingApproval(Donation donation) {
        String donorName = donation.isAnonymous() ? "Anonymous" : getDonorName(donation);
        String campaignTitle = donation.getCampaign().getTitle();
        String linkUrl = "/campaigns/" + donation.getCampaign().getId() + "/approvals";
        String message = String.format(
                "Donor '%s' has submitted a manual donation of %s VND for your campaign '%s' and is pending your approval.",
                donorName,
                formatVnd(donation.getAmount()),
                campaignTitle);

        User creator = donation.getCampaign().getUser();
        if (creator != null) {
            sendNotification(creator, "New Pending Donation", message, linkUrl);
        }

        Long creatorId = creator == null ? null : creator.getId();
        List<User> campaignAdmins = campaignMemberRepository.findUsersByCampaignIdAndRole(
                donation.getCampaign().getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
        campaignAdmins.stream()
                .filter(admin -> !admin.getId().equals(creatorId))
                .forEach(admin -> sendNotification(admin, "New Pending Donation", message, linkUrl));
    }

    public void publishRejected(Donation donation, String reason) {
        if (donation.getUser() == null) {
            return;
        }

        String message = String.format(
                "Your donation of %s VND for campaign '%s' was rejected by the campaign admin. Reason: %s",
                formatVnd(donation.getAmount()),
                donation.getCampaign().getTitle(),
                reason);
        sendNotification(
                donation.getUser(),
                "Donation Rejected",
                message,
                "/campaigns/" + donation.getCampaign().getId() + "?rejectedDonationId=" + donation.getId());
    }

    private void sendNotification(User recipientUser, String title, String message, String linkUrl) {
        try {
            NotificationRecipient recipient = new NotificationRecipient(
                    recipientUser.getId(), recipientUser.getEmail());
            CreateNotificationCommand command = CreateNotificationCommand.builder()
                    .recipients(Set.of(recipient))
                    .type(NotificationType.DONATION)
                    .title(title)
                    .message(message)
                    .linkUrl(linkUrl)
                    .build();
            notificationService.createNotification(command);
        } catch (Exception exception) {
            log.error("Failed to send donation notification to user: {}", recipientUser.getEmail(), exception);
        }
    }

    private String formatVnd(Long amount) {
        return amount == null ? "0" : NumberFormat.getNumberInstance(Locale.GERMANY).format(amount);
    }

    private String getDonorName(Donation donation) {
        if (donation.getUser() == null) {
            return "Unknown donor";
        }

        if (donation.getUser().getFullName() != null && !donation.getUser().getFullName().isBlank()) {
            return donation.getUser().getFullName();
        }

        return donation.getUser().getEmail();
    }
}
