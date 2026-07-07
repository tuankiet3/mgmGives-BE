package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.event.notification.*;
import com.mgmtp.gives.notification.NotificationCommandFactory;
import com.mgmtp.gives.notification.NotificationRecipientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationCommandFactoryImpl implements NotificationCommandFactory {
    private final NotificationRecipientResolver recipientResolver;

    public CreateNotificationCommand donationConfirmed(DonationConfirmedEvent event) {
        String contributionText = formatContribution(
                event.donationType(),
                event.amount()
        );

        return CreateNotificationCommand.builder()
                .recipients(recipientResolver.singleUser(event.donorUserId()))
                .type(NotificationType.DONATION_CONFIRMED)
                .title("Your kindness just got confirmed ✨")
                .message(
                        "Your donation of " + contributionText +
                                " to campaign \"" + event.campaignTitle() +
                                "\" has been confirmed. Kindness looks good on you 😄"
                )
                .linkUrl("/my-donations")
                .build();
    }

    public CreateNotificationCommand campaignDonationConfirmed(CampaignDonationConfirmedEvent event) {
        Set<NotificationRecipient> recipients =
                recipientResolver.campaignOwnerAndFollowersExceptDonor(
                        event.campaignId(),
                        event.donorUserId()
                );

        if (event.confirmedById() != null) {
            recipients.removeIf(r -> r.userId().equals(event.confirmedById()));
        }

        recipients.forEach(recipient -> log.info("Recipient: {}", recipient.userId()));

        log.info(
                "Campaign new donation notification command created: donationId={}, campaignId={}, recipientCount={}",
                event.donationId(),
                event.campaignId(),
                recipients.size()
        );
        String donorDisplayName = event.anonymous() ? "*****" : event.donorName();

        String contributionText = formatContribution(
                event.donationType(),
                event.amount()
        );

        return CreateNotificationCommand.builder()
                .recipients(recipients)
                .type(NotificationType.CAMPAIGN_DONATION_CONFIRMED)
                .title("New donation confirmed 🎉")
                .message(
                        donorDisplayName +
                                " donated " + contributionText +
                                " to campaign \"" + event.campaignTitle() + "\"."
                )
                .linkUrl("/campaigns/" + event.campaignId())
                .build();
    }

    public CreateNotificationCommand campaignStatusChanged(CampaignStatusChangedEvent event) {
        Set<NotificationRecipient> recipients = event.oldStatus() == CampaignStatus.PENDING
                ? recipientResolver.campaignOwner(event.campaignId())
                : recipientResolver.campaignOwnerAndFollowers(event.campaignId());

        return CreateNotificationCommand.builder()
                .recipients(recipients)
                .type(NotificationType.CAMPAIGN_STATUS_CHANGED)
                .title(buildCampaignStatusChangedTitle(event.newStatus()))
                .message(
                        "Campaign \"" + event.campaignTitle() +
                                "\" status changed from " + formatCampaignStatus(event.oldStatus()) +
                                " to " + formatCampaignStatus(event.newStatus()) + "."
                )
                .linkUrl("/campaigns/" + event.campaignId())
                .build();
    }

    private String buildCampaignStatusChangedTitle(CampaignStatus newStatus) {
        return switch (newStatus) {
            case APPROVED -> "Campaign approved";
            case REJECTED -> "Campaign rejected";
            case IN_PROGRESS -> "Campaign started";
            case COMPLETED -> "Campaign completed";
            default -> "Campaign status updated";
        };
    }

    private String formatCampaignStatus(CampaignStatus status) {
        if (status == null) {
            return "Unknown";
        }

        return switch (status) {
            case DRAFT -> "Draft";
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case IN_PROGRESS -> "In progress";
            case COMPLETED -> "Completed";
        };
    }

    private String formatContribution(DonationType donationType, Long amount) {
        if (donationType == DonationType.MONEY) return formatVnd(amount);
        return "some goods";
    }

    private String formatVnd(Long amount) {
        if (amount == null) {
            return "0 VND";
        }

        return String.format("%,d VND", amount);
    }
}
