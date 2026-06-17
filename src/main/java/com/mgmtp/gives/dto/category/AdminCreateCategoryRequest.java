package com.mgmtp.gives.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 255, message = "Category name must be between 2 and 255 characters")
        @Schema(description = "Category name", example = "Education & Training")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        @Schema(description = "Category description", example = "Funding for scholarships and tutoring programs")
        String description
) {
}
