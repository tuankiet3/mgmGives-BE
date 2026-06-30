package com.mgmtp.gives.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminCategoryResponse(
                @Schema(description = "The ID of the category", example = "1") Long id,

                @Schema(description = "The name of the category", example = "Education & Training") String name,

                @Schema(description = "The description of the category", example = "Funding for scholarships and tutoring programs") String description,
                @Schema(description = "The soft-deletion timestamp of the category", example = "2026-06-30T10:41:28") LocalDateTime deletedAt,
                @Schema(description = "The number of campaigns in this category", example = "5") Long campaignsCount) {
}
