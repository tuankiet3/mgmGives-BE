package com.mgmtp.gives.event.notification;

public record CampaignUnjoinRequestedEvent(
        Long campaignId,
        String campaignTitle,
        Long requesterId,
        String requesterName
) {
}
