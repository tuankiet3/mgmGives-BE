package com.mgmtp.gives.dto.campaign_spending;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateCampaignSpendingRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Max(value = 500_000_000_000L, message = "Amount must not exceed 500,000,000,000")
        Long amount,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @NotNull(message = "Spent date is required")
        LocalDate spentAt
) {
}
