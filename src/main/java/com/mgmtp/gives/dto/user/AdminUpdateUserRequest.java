package com.mgmtp.gives.dto.user;

import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
        @Schema(description = "The updated full name of the user", example = "Jane Smith")
        String fullName,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        @Schema(description = "The updated phone number of the user", example = "+84 901 234 568")
        String phone,

        @NotNull(message = "Role is required")
        @Schema(description = "The updated role of the user", example = "ADMIN")
        UserRole role,

        @NotNull(message = "Status is required")
        @Schema(description = "The updated status of the user", example = "ACTIVE")
        UserStatus status,

        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        @Schema(description = "The updated password of the user (leave blank to keep unchanged)", example = "newSecurePassword123")
        String password
) {
}
