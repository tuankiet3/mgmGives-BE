package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.category.AdminCreateCategoryRequest;
import com.mgmtp.gives.dto.category.AdminUpdateCategoryRequest;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface AdminCategoryService extends CategoryService {
    Page<Category> getAllCategories(Collection<CategoryStatus> statuses, String search, Pageable pageable);

    Category createCategory(AdminCreateCategoryRequest request);

    Category updateCategory(Long id, AdminUpdateCategoryRequest request);

    void deleteCategory(Long id);
}
