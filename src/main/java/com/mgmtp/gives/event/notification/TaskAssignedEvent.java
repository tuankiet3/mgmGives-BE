package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import java.util.Set;

public record TaskAssignedEvent(
        Long campaignId,
        Long taskId,
        String taskTitle,
        String taskDescription,
        Set<NotificationRecipient> assignees
) {
}
