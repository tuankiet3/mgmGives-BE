package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.dto.notification.NotificationRecipient;

public record TaskUnassignedEvent(
        Long campaignId,
        Long taskId,
        String taskTitle,
        NotificationRecipient recipient
) {
}
