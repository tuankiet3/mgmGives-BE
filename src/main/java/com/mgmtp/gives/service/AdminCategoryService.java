package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.category.AdminCategoryResponse;
import com.mgmtp.gives.dto.category.AdminCreateCategoryRequest;
import com.mgmtp.gives.dto.category.AdminUpdateCategoryRequest;
import com.mgmtp.gives.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface AdminCategoryService {
    Page<AdminCategoryResponse> getAllCategories(Collection<CategoryStatus> statuses, String search, Pageable pageable);

    AdminCategoryResponse createCategory(AdminCreateCategoryRequest request);

    AdminCategoryResponse updateCategory(Long id, AdminUpdateCategoryRequest request);

    AdminCategoryResponse getCategoryById(Long id);

    void deleteCategory(Long id);
}

