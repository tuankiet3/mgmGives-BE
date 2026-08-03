package com.mgmtp.gives.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryResponse(
        @Schema(description = "The ID of the category", example = "1") Long id,

        @Schema(description = "The name of the category", example = "Education & Training") String name,

        @Schema(description = "The description of the category", example = "Funding for scholarships and tutoring programs") String description) {
}
