package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.enums.CampaignPriority;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;

public record CampaignRequest(
        @NotBlank(message = "Title is required") @Size(max = 255, message = "Title must not exceed 255 characters") String title,

        @NotBlank(message = "Description is required") String description,

        @NotNull(message = "Start date is required") LocalDateTime startDate,

        @NotNull(message = "End date is required") LocalDateTime endDate,

        @NotNull(message = "Target amount is required") @Min(value = 1, message = "Target amount must be a positive number") Long target,

        @NotNull(message = "Priority is required") CampaignPriority priority,

        @NotEmpty(message = "At least one category is required") Set<Long> categories) {
}
