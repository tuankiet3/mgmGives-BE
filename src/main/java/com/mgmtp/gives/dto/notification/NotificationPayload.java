package com.mgmtp.gives.dto.notification;

import com.mgmtp.gives.enums.NotificationType;
import lombok.Builder;

@Builder
public record NotificationPayload(
        Long notificationId,
        NotificationType type,
        String title,
        String message,
        String linkUrl
) {

}
