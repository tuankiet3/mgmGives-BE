package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.category.UserCategoryResponse;
import com.mgmtp.gives.service.UserCategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public-facing category endpoints consumed by the campaign creation flow (DANANG-1765).
 *
 * <p>Security summary:
 * <ul>
 *   <li>GET  /api/categories              — public (no auth required)</li>
 * </ul>
 * Access control is enforced in {@code SecurityConfig}, not here.
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Public Category", description = "Public endpoints for retrieving categories")
public class UserCategoryController {

    private final UserCategoryService userCategoryService;

    public UserCategoryController(UserCategoryService userCategoryService) {
        this.userCategoryService = userCategoryService;
    }

    /**
     * Returns all active categories sorted by name.
     * Public endpoint — no authentication required.
     * Used by the campaign creation flow to populate the selectable category list.
     */
    @GetMapping
    @Operation(
            summary = "Get approved categories",
            description = "Returns all categories, sorted by name ascending. Public access."
    )
    public ApiResponse<List<UserCategoryResponse>> getApprovedCategories() {
        return ApiResponse.success(userCategoryService.getApprovedCategories());
    }
}
