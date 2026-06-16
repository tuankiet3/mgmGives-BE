package com.mgmtp.gives.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email(message = "Email is invalid")
        @NotBlank(message = "Email is required")
        @Schema(
                description = "User's corporate or personal email address",
                example = "areyouok1111@gmail.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Password must contain at least one letter and one number"
        )
        @Schema(
                description = "Secure password for the account",
                example = "WeakPass123!",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String password,

        @NotBlank(message = "FullName is required")
        @Schema(
                description = "Display full name of the user",
                example = "Eric Wilton",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String fullName
) {
}
