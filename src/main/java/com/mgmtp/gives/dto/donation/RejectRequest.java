package com.mgmtp.gives.dto.donation;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(
        @NotBlank(message = "Reject reason is required")
        String reason
) {
}
