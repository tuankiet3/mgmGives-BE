package com.mgmtp.gives.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Result of importing users from a CSV file")
public record AdminBulkImportUserResponse(
        @Schema(description = "Number of users created", example = "10")
        int createdCount,

        @Schema(description = "Created users. Empty for successful bulk imports to keep large imports fast.")
        List<AdminUserResponse> users,

        @Schema(description = "Row-level validation errors. Empty when import succeeds.")
        List<AdminBulkImportUserError> errors
) {
}
