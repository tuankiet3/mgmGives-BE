package com.mgmtp.gives.dto.campaign_spending;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCampaignSpendingRequest(
        @Positive(message = "Amount must be greater than zero")
        @Max(value = 500_000_000_000L, message = "Amount must not exceed 500,000,000,000")
        Long amount,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        LocalDate spentAt
) {
}
