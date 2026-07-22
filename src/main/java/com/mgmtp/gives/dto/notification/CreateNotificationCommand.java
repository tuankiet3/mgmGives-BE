package com.mgmtp.gives.dto.notification;

import com.mgmtp.gives.enums.NotificationType;
import lombok.Builder;

import java.util.Set;

@Builder
public record CreateNotificationCommand(
        Set<NotificationRecipient> recipients,
        NotificationType type,
        String title,
        String message,
        String linkUrl
) {
}
