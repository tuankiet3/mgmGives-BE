package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.enums.TaskStatus;
import java.util.Set;

public record TaskStatusChangedEvent(
        Long campaignId,
        Long taskId,
        String taskTitle,
        TaskStatus oldStatus,
        TaskStatus newStatus,
        Set<NotificationRecipient> recipients
) {
}
