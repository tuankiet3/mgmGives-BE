package com.mgmtp.gives.dto.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        Boolean isRead,
        String linkUrl,
        LocalDateTime createdAt
) {
}
