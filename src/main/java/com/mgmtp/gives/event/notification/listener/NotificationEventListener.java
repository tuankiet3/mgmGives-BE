package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.event.notification.*;
import com.mgmtp.gives.notification.NotificationCommandFactory;
import com.mgmtp.gives.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationEventListener {
    private final NotificationService notificationService;
    private final NotificationCommandFactory notificationCommandFactory;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDonationApproved(DonationConfirmedEvent event) {
        log.info("Notification event received: donation approved, donationId={}", event.donationId());

        notificationService.createNotification(
                notificationCommandFactory.donationConfirmed(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignNewDonation(CampaignDonationConfirmedEvent event) {
        log.info("Notification event received: campaign new donation, donationId={}", event.donationId());

        notificationService.createNotification(
                notificationCommandFactory.campaignDonationConfirmed(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCampaignStatusChanged(CampaignStatusChangedEvent event) {
        log.info(
                "Handling campaign status changed event: campaignId={}, oldStatus={}, newStatus={}",
                event.campaignId(),
                event.oldStatus(),
                event.newStatus()
        );

        notificationService.createNotification(
                notificationCommandFactory.campaignStatusChanged(event)
        );
    }
}
