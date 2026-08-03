package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import java.time.LocalDateTime;
import java.util.Set;

public record TaskCreatedEmailEvent(
        Long campaignId,
        String campaignName,
        String taskTitle,
        String taskDescription,
        LocalDateTime dueDate,
        Set<NotificationRecipient> assignees
) {
}
