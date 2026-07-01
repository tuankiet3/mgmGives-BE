package com.mgmtp.gives.dto.donation;

public record PayOSResponse(
        Long donationId,
        String checkoutUrl,
        Long amount
) {}
