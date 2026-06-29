package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
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
                event.amount(),
                event.goodsDescription()
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
                .linkUrl("/donations/me")
                .build();
    }

    public CreateNotificationCommand campaignDonationConfirmed(CampaignDonationConfirmedEvent event) {
        Set<NotificationRecipient> recipients =
                recipientResolver.campaignOwnerAndFollowersExceptDonor(
                        event.campaignId(),
                        event.donorUserId()
                );

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
                event.amount(),
                event.goodsDescription()
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
                .linkUrl("/admin/campaigns/" + event.campaignId())
                .build();
    }

    private String formatContribution(DonationType donationType, Long amount, String goodsDescription) {
        if (donationType == DonationType.MONEY) {
            return formatVnd(amount);
        }

//        if (goodsDescription != null && !goodsDescription.isBlank()) {
//            return "goods: " + goodsDescription;
//        }

        return "some goods";
    }

    private String formatVnd(Long amount) {
        if (amount == null) {
            return "0 VND";
        }

        return String.format("%,d VND", amount);
    }
}
