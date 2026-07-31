package com.mgmtp.gives.event.task;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskChangedPayload;
import com.mgmtp.gives.entity.User;

public record CampaignTaskConflictEvent(
        Long taskId,
        User recipient,
        CampaignTaskChangedPayload payload
) {
}
