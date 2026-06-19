package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.category.UserCategoryResponse;
import com.mgmtp.gives.dto.category.UserSuggestCategoryRequest;
import com.mgmtp.gives.service.UserCategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public-facing category endpoints consumed by the campaign creation flow (DANANG-1765).
 *
 * <p>Security summary:
 * <ul>
 *   <li>GET  /api/categories              — public (no auth required)</li>
 *   <li>POST /api/categories/suggestions  — authenticated users only</li>
 * </ul>
 * Access control is enforced in {@code SecurityConfig}, not here.
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Public Category", description = "Public endpoints for retrieving and suggesting categories")
public class UserCategoryController {

    private final UserCategoryService userCategoryService;

    public UserCategoryController(UserCategoryService userCategoryService) {
        this.userCategoryService = userCategoryService;
    }

    /**
     * Returns all APPROVED categories sorted by name.
     * Public endpoint — no authentication required.
     * Used by the campaign creation flow to populate the selectable category list.
     */
    @GetMapping
    @Operation(
            summary = "Get approved categories",
            description = "Returns all categories with APPROVED status, sorted by name ascending. Public access."
    )
    public ApiResponse<List<UserCategoryResponse>> getApprovedCategories() {
        return ApiResponse.success(userCategoryService.getApprovedCategories());
    }

    /**
     * Accepts a new category suggestion from an authenticated user.
     * The category is saved with status PENDING and must be reviewed by an admin.
     * Status is never accepted from the request body — it is always forced to PENDING.
     */
    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Suggest a new category",
            description = "Allows authenticated users to suggest a new category. Saved as PENDING for admin review."
    )
    public ApiResponse<UserCategoryResponse> suggestCategory(
            @Valid @RequestBody UserSuggestCategoryRequest request) {
        return ApiResponse.success(userCategoryService.suggestCategory(request));
    }
}
