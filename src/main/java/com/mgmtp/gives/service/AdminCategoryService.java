package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.category.AdminCategoryResponse;
import com.mgmtp.gives.dto.category.AdminCreateCategoryRequest;
import com.mgmtp.gives.dto.category.AdminUpdateCategoryRequest;
import com.mgmtp.gives.dto.category.CategoryDeleteCheckResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminCategoryService {
    Page<AdminCategoryResponse> getAllCategories(boolean showDeleted, String search, Pageable pageable);

    AdminCategoryResponse createCategory(AdminCreateCategoryRequest request);

    AdminCategoryResponse updateCategory(Long id, AdminUpdateCategoryRequest request);

    AdminCategoryResponse getCategoryById(Long id);

    void deleteCategory(Long id);

    void permanentDeleteCategory(Long id);

    AdminCategoryResponse restoreCategory(Long id);

    CategoryDeleteCheckResponse checkCategoryDeletion(Long id);
}

