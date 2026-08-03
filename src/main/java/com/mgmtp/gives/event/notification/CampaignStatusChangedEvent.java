package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.enums.CampaignStatus;

public record CampaignStatusChangedEvent(
        Long campaignId,
        String campaignTitle,
        CampaignStatus oldStatus,
        CampaignStatus newStatus
) {
}
