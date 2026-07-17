package com.mgmtp.gives.notification;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.event.notification.*;

public interface NotificationCommandFactory {
    CreateNotificationCommand donationConfirmed(DonationConfirmedEvent event);

    CreateNotificationCommand campaignDonationConfirmed(CampaignDonationConfirmedEvent event);

    CreateNotificationCommand campaignStatusChanged(CampaignStatusChangedEvent event);

    CreateNotificationCommand taskAssigned(TaskAssignedEvent event);

    CreateNotificationCommand taskStatusChanged(TaskStatusChangedEvent event);

    CreateNotificationCommand taskDescriptionUpdated(TaskDescriptionUpdatedEvent event);

    CreateNotificationCommand taskUnassigned(TaskUnassignedEvent event);
}
