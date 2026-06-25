package com.mgmtp.gives.dto.donation;

public record DonationNotification(
        Long donationId,
        String status,
        String message
) {
}
