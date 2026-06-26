package com.mgmtp.gives.dto.user;

import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        @Schema(description = "The email address of the new user", example = "jane.doe@example.com")
        String email,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
        @Schema(description = "The full name of the new user", example = "Jane Doe")
        String fullName,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        @Schema(description = "The optional phone number of the user", example = "+84 901 234 567")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        @Schema(description = "The password for the new user", example = "securePassword123")
        String password,

        @NotNull(message = "Role is required")
        @Schema(description = "The role assigned to the new user", example = "USER")
        UserRole role,

        @NotNull(message = "Status is required")
        @Schema(description = "The initial status of the new user", example = "ACTIVE")
        UserStatus status
) {
}
