package com.mgmtp.gives.dto.donation;

public record VNPayResponse(
        Long donationId,
        String qrCodeUrl,
        Long amount,
        String transactionId
) {
}
