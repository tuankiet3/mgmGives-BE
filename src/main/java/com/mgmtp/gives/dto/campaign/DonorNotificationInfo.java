package com.mgmtp.gives.dto.campaign;

public record DonorNotificationInfo(
        Long userId,
        String email,
        String fullName,
        Long totalAmount
) {
}
