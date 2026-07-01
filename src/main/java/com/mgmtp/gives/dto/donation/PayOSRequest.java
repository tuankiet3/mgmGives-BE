package com.mgmtp.gives.dto.donation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PayOSRequest(
        @NotNull(message = "Campaign ID is required")
        Long campaignId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Max(value = 999_999_999_999L, message = "Amount must not exceed 999,999,999,999")
        Long amount,

        boolean anonymous,

        @Size(max = 280, message = "Message must not exceed 280 characters")
        String message
) {}
