package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;

public record CampaignRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,
        Set<Long> categories,
        Boolean acceptsMoney,
        Boolean acceptsGoods,
        Long target,
        LocalDateTime startDate,
        LocalDateTime endDate,
        CampaignPriority priority,
        CampaignStatus status
) {
}
