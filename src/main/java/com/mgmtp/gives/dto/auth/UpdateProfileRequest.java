package com.mgmtp.gives.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank(message = "FullName is required")
        @Schema(description = "The updated full name of the user", example = "Eric Wilton", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,

        @Schema(description = "The updated phone number of the user", example = "+84 901 234 567")
        String phone
) {
}
