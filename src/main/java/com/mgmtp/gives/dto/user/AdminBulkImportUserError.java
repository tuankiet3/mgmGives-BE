package com.mgmtp.gives.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Validation error for a user CSV import row")
public record AdminBulkImportUserError(
        @Schema(description = "CSV row number, including the header row", example = "2")
        int row,

        @Schema(description = "Column name that failed validation", example = "email")
        String field,

        @Schema(description = "Human-readable validation message", example = "Email is required")
        String message
) {
}
