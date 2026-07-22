package com.mgmtp.gives.dto.campaign_label;

import jakarta.validation.constraints.Size;

public record UpdateCampaignLabelRequest(
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Size(max = 50, message = "Color must not exceed 50 characters")
        String color
) {
}
