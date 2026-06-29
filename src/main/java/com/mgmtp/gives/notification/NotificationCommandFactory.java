package com.mgmtp.gives.notification;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.event.notification.*;

public interface NotificationCommandFactory {
    CreateNotificationCommand donationConfirmed(DonationConfirmedEvent event);

    CreateNotificationCommand campaignDonationConfirmed(CampaignDonationConfirmedEvent event);

    CreateNotificationCommand campaignStatusChanged(CampaignStatusChangedEvent event);
}
