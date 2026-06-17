package com.mgmtp.gives.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserSuggestCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 255, message = "Category name must be between 2 and 255 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description
) {
}
