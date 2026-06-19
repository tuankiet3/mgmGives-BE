package com.mgmtp.gives.dto.category;

/**
 * Public-facing response DTO for category data.
 * Intentionally does NOT expose {@code status} — that is internal workflow data
 * and must never leak to the public API consumed by DANANG-1765 frontend.
 */
public record UserCategoryResponse(
        Long id,
        String name,
        String description
) {}
