package com.mgmtp.gives.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Email(message = "Email is invalid")
        @NotBlank(message = "Email is required")
        @Schema(
                description = "User's registered email address",
                example = "areyouok1111@gmail.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email
) {
}
