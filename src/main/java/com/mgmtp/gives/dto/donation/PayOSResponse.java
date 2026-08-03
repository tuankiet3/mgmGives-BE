package com.mgmtp.gives.dto.donation;

public record PayOSResponse(
        Long donationId,
        String checkoutUrl,
        Long amount,
        String qrCode,
        String bin,
        String accountNumber,
        String accountName,
        String description
) {}
