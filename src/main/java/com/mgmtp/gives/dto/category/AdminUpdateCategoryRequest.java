package com.mgmtp.gives.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUpdateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 255, message = "Category name must be between 2 and 255 characters")
        @Schema(description = "The updated name", example = "Disaster Relief Operations")
        String name,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        @Schema(description = "The updated optional description", example = "Updated funding details")
        String description
) {
}
