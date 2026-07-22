package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import java.util.Set;

public record TaskDescriptionUpdatedEvent(
        Long campaignId,
        Long taskId,
        String taskTitle,
        Set<NotificationRecipient> recipients
) {
}
