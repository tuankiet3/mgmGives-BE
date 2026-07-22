package com.mgmtp.gives.dto.donation;

import jakarta.validation.constraints.NotBlank;

public record EditDonationRequest(
        @NotBlank(message = "Reason is required")
        String reason
) {
}
