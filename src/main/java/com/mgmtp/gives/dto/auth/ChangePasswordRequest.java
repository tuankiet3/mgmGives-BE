package com.mgmtp.gives.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Schema(description = "The current password of the user", example = "OldPass123!", requiredMode = Schema.RequiredMode.REQUIRED)
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Password must contain at least one letter and one number"
        )
        @Schema(description = "The new password for the user", example = "NewPass123!", requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword
) {
}
