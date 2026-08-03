package com.mgmtp.gives.dto.user;

import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AdminUserResponse(
        @Schema(description = "The ID of the user", example = "1")
        Long id,

        @Schema(description = "The email address of the user", example = "admin@example.com")
        String email,

        @Schema(description = "The full name of the user", example = "John Doe")
        String fullName,

        @Schema(description = "The phone number of the user", example = "+84 901 234 567")
        String phone,

        @Schema(description = "The avatar URL of the user", example = "https://example.com/avatar.jpg")
        String avatarUrl,

        @Schema(description = "The role of the user", example = "ADMIN")
        UserRole role,

        @Schema(description = "The status of the user", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "The date and time when the user was created")
        LocalDateTime createdAt,

        @Schema(description = "The date and time when the user was last updated")
        LocalDateTime updatedAt
) {
}
