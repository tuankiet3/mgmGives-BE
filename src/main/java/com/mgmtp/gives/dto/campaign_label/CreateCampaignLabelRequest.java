package com.mgmtp.gives.dto.campaign_label;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCampaignLabelRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Color is required")
        @Size(max = 50, message = "Color must not exceed 50 characters")
        String color
) {
}
