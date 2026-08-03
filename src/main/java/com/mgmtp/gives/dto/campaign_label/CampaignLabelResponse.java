package com.mgmtp.gives.dto.campaign_label;

public record CampaignLabelResponse(
        Long id,
        Long campaignId,
        String name,
        String color
) {
}
