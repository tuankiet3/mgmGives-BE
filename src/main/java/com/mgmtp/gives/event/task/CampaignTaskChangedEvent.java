package com.mgmtp.gives.event.task;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskChangedPayload;

import java.util.Set;

public record CampaignTaskChangedEvent(
        CampaignTaskChangedPayload payload,
        Set<String> recipientEmails
) {
}
