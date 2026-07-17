package com.mgmtp.gives.event.notification;

public record CampaignUnjoinRejectedEvent(
        Long campaignId,
        String campaignTitle,
        Long userId,
        String reason
) {
}
