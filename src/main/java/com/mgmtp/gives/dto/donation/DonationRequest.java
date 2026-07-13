package com.mgmtp.gives.dto.donation;

import com.mgmtp.gives.enums.DonationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DonationRequest(
        @NotNull(message = "Campaign ID is required")
        Long campaignId,

        @NotNull(message = "Donation type is required")
        DonationType donationType,

        @Positive(message = "Amount must be greater than zero")
        @Max(value = 999_999_999_999L, message = "Amount must not exceed 999,999,999,999")
        Long amount,

        String goodsDescription,

        String detail,

        boolean anonymous,

        String transactionId,

        String transactionDescription,

        String transactionProofUrl,

        @Size(max = 280, message = "Message must not exceed 280 characters")
        String message,

        String goodsCondition,

        String goodsCategory,

        String deliveryMethod
) {
}
