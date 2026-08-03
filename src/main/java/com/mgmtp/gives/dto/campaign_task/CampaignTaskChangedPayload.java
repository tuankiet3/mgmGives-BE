package com.mgmtp.gives.dto.campaign_task;

import java.time.LocalDateTime;

public record CampaignTaskChangedPayload(
        String type,
        CampaignTaskChangeAction action,
        Long campaignId,
        Long taskId,
        Long version,
        CampaignTaskResponse task,
        LocalDateTime updatedAt,
        Long updatedBy
) {
}
