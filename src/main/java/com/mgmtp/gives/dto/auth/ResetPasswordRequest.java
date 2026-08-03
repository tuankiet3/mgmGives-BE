package com.mgmtp.gives.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        @Schema(
                description = "The token sent via email for password reset",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String token,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Password must contain at least one letter and one number"
        )
        @Schema(
                description = "New password for the account",
                example = "mgmGives@123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                message = "Password must contain at least one letter and one number"
        )
        @Schema(
                description = "Confirmation of the new password",
                example = "mgmGives@123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String confirmNewPassword
) {
}
