package com.mgmtp.gives.dto.notification;

public record NotificationRecipient(
        Long userId,
        String email
) {
}
