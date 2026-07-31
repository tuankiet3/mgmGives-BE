package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.category.AdminCategoryResponse;
import com.mgmtp.gives.dto.category.AdminCreateCategoryRequest;
import com.mgmtp.gives.dto.category.AdminUpdateCategoryRequest;
import com.mgmtp.gives.dto.category.CategoryDeleteCheckResponse;
import com.mgmtp.gives.service.AdminCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/categories")
@Tag(name = "Admin Category Management", description = "Endpoints for administrator to manage categories")
public class AdminCategoryController {
    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    /**
     * Endpoint to create a new category directly by an Admin.
     */
    @PostMapping
    @Operation(summary = "Create a new category", description = "Creates a new category with the specified details.")
    public ApiResponse<?> createCategory(@Valid @RequestBody AdminCreateCategoryRequest request) {

        AdminCategoryResponse response = adminCategoryService.createCategory(request);

        // Return HTTP 201 Created along with the saved data
        return ApiResponse.success(response, "Category Created Successfully");
    }

    @GetMapping
    @Operation(summary = "Get all categories", description = "Retrieves a list of categories, optionally filtered by deletion status.")
    public ApiResponse<?> getAllCategories(
            @Parameter(description = "Optional flag to retrieve deleted categories instead of active ones", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean showDeleted,
            @Parameter(description = "Optional search query to filter categories by name or description", example = "relief")
            @RequestParam(required = false) String search,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {


        Page<AdminCategoryResponse> responsePage = adminCategoryService.getAllCategories(showDeleted, search, pageable);

        PageResponse<AdminCategoryResponse> pageResponse = PageResponse.of(responsePage, responsePage.getContent());
        return ApiResponse.success(pageResponse);
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Retrieves details of a single category by its ID.")
    public ApiResponse<?> getCategoryById(
            @Parameter(description = "The ID of the category", required = true, example = "1")
            @PathVariable Long id) {
        AdminCategoryResponse response = adminCategoryService.getCategoryById(id);
        return ApiResponse.success(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Updates details of an existing category by its ID.")
    public ApiResponse<?> updateCategory(
            @Parameter(description = "The ID of the category to update", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateCategoryRequest request) {

        AdminCategoryResponse response = adminCategoryService.updateCategory(id, request);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category", description = "Soft-deletes an existing category by its ID.")
    public ApiResponse<?> deleteCategory(
            @Parameter(description = "The ID of the category to delete", required = true, example = "1")
            @PathVariable Long id) {
        adminCategoryService.deleteCategory(id);

        return ApiResponse.<Void>success(null);
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Permanently delete category", description = "Permanently deletes an archived category by its ID.")
    public ApiResponse<?> permanentDeleteCategory(
            @Parameter(description = "The ID of the category to permanently delete", required = true, example = "1")
            @PathVariable Long id) {
        adminCategoryService.permanentDeleteCategory(id);

        return ApiResponse.<Void>success(null);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore category", description = "Restores a soft-deleted category by its ID.")
    public ApiResponse<?> restoreCategory(
            @Parameter(description = "The ID of the category to restore", required = true, example = "1")
            @PathVariable Long id) {
        AdminCategoryResponse response = adminCategoryService.restoreCategory(id);
        return ApiResponse.success(response, "Category Restored Successfully");
    }

    @GetMapping("/{id}/delete-check")
    @Operation(summary = "Check category usage before deletion", description = "Returns the count of campaigns using this category and where this is the only category.")
    public ApiResponse<CategoryDeleteCheckResponse> checkCategoryDeletion(
            @Parameter(description = "The ID of the category to check", required = true, example = "1")
            @PathVariable Long id) {
        CategoryDeleteCheckResponse response = adminCategoryService.checkCategoryDeletion(id);
        return ApiResponse.success(response);
    }
}
