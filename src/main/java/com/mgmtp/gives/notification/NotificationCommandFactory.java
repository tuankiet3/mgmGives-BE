package com.mgmtp.gives.notification;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.event.notification.*;

import java.util.List;

public interface NotificationCommandFactory {
    CreateNotificationCommand donationConfirmed(DonationConfirmedEvent event);

    CreateNotificationCommand campaignDonationConfirmed(CampaignDonationConfirmedEvent event);

    CreateNotificationCommand campaignStatusChanged(CampaignStatusChangedEvent event);

    CreateNotificationCommand taskAssigned(TaskAssignedEvent event);

    CreateNotificationCommand taskStatusChanged(TaskStatusChangedEvent event);

    CreateNotificationCommand taskDescriptionUpdated(TaskDescriptionUpdatedEvent event);

    CreateNotificationCommand taskUnassigned(TaskUnassignedEvent event);

    CreateNotificationCommand campaignUnjoinRequested(CampaignUnjoinRequestedEvent event);

    CreateNotificationCommand campaignUnjoinApproved(CampaignUnjoinApprovedEvent event);

    CreateNotificationCommand campaignUnjoinRejected(CampaignUnjoinRejectedEvent event);

    List<CreateNotificationCommand> announcementReplyCreated(AnnouncementReplyCreatedEvent event);
}
