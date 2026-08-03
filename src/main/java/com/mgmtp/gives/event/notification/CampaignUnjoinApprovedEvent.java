package com.mgmtp.gives.event.notification;

public record CampaignUnjoinApprovedEvent(
        Long campaignId,
        String campaignTitle,
        Long userId
) {
}
