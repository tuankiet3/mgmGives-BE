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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskAssigned(TaskAssignedEvent event) {
        log.info(
                "Handling task assigned event: campaignId={}, taskId={}, recipientCount={}",
                event.campaignId(),
                event.taskId(),
                event.assignees().size()
        );

        notificationService.createNotification(
                notificationCommandFactory.taskAssigned(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskStatusChanged(TaskStatusChangedEvent event) {
        log.info(
                "Handling task status changed event: campaignId={}, taskId={}, oldStatus={}, newStatus={}, recipientCount={}",
                event.campaignId(),
                event.taskId(),
                event.oldStatus(),
                event.newStatus(),
                event.recipients().size()
        );

        notificationService.createNotification(
                notificationCommandFactory.taskStatusChanged(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskDescriptionUpdated(TaskDescriptionUpdatedEvent event) {
        log.info(
                "Handling task description updated event: campaignId={}, taskId={}, recipientCount={}",
                event.campaignId(),
                event.taskId(),
                event.recipients().size()
        );

        notificationService.createNotification(
                notificationCommandFactory.taskDescriptionUpdated(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskUnassigned(TaskUnassignedEvent event) {
        log.info(
                "Handling task unassigned event: campaignId={}, taskId={}, recipientUserId={}",
                event.campaignId(),
                event.taskId(),
                event.recipient().userId()
        );

        notificationService.createNotification(
                notificationCommandFactory.taskUnassigned(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCampaignUnjoinRequested(CampaignUnjoinRequestedEvent event) {
        log.info(
                "Notification event received: campaign unjoin requested, campaignId={}, requesterId={}",
                event.campaignId(), event.requesterId()
        );

        notificationService.createNotification(
                notificationCommandFactory.campaignUnjoinRequested(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCampaignUnjoinApproved(CampaignUnjoinApprovedEvent event) {
        log.info(
                "Notification event received: campaign unjoin approved, campaignId={}, userId={}",
                event.campaignId(), event.userId()
        );

        notificationService.createNotification(
                notificationCommandFactory.campaignUnjoinApproved(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCampaignUnjoinRejected(CampaignUnjoinRejectedEvent event) {
        log.info(
                "Notification event received: campaign unjoin rejected, campaignId={}, userId={}",
                event.campaignId(), event.userId()
        );

        notificationService.createNotification(
                notificationCommandFactory.campaignUnjoinRejected(event)
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAnnouncementReplyCreated(AnnouncementReplyCreatedEvent event) {
        log.info("Handling announcement reply notification: announcementId={}, replyId={}",
                event.announcementId(), event.replyId());

        notificationCommandFactory.announcementReplyCreated(event).stream()
                .filter(command -> !command.recipients().isEmpty())
                .forEach(notificationService::createNotification);
    }
}
