package com.mgmtp.gives.dto.campaign_task;

import com.mgmtp.gives.enums.CampaignTaskActivityAction;

import java.time.LocalDateTime;
import java.util.Map;

public record CampaignTaskActivityResponse(
        Long id,
        CampaignTaskActivityAction action,
        ActorSummary actor,
        Map<String, Object> details,
        LocalDateTime createdAt
) {
    public record ActorSummary(
            Long id,
            String name,
            String avatarUrl
    ) {
    }
}
