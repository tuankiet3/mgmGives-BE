package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.category.AdminCreateCategoryRequest;
import com.mgmtp.gives.dto.category.AdminUpdateCategoryRequest;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CategoryMapper;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.service.AdminCategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NAME_ALREADY_EXISTS;
import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NOT_FOUND;

@Service
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public AdminCategoryServiceImpl(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }


    /**
     * Handles the creation of a new category with a business check for duplicate names.
     */
    @Override
    @Transactional
    public Category createCategory(AdminCreateCategoryRequest request) {
        Category category = categoryMapper.toEntity(request);

        // 1. Business Logic: Check if the case-insensitive name already exists
        if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
            throw new AppException(CATEGORY_NAME_ALREADY_EXISTS, "Category with name '" + category.getName() + "' already exists.");
        }

        // 2. If it's unique, save it to the database
        return categoryRepository.save(category);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Category> getAllCategories(Collection<CategoryStatus> statuses, Pageable pageable) {
        if (statuses == null || statuses.isEmpty()) {
            return categoryRepository.findAll(pageable);
        }

        return categoryRepository.findAllByStatusIn(statuses, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Category> getAllCategories(Pageable pageable) {
        // Delegate to the status-filtered method with null (meaning "all statuses")
        return getAllCategories(null, pageable);
    }


    @Override
    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CATEGORY_NOT_FOUND));
    }


    @Override
    @Transactional
    public Category updateCategory(Long id, AdminUpdateCategoryRequest updatedData) {
        // Blow up if not found
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CATEGORY_NOT_FOUND, "Category not found with id=" + id));

        // Business logic: avoid duplicate when renaming
        if (!existingCategory.getName().equalsIgnoreCase(updatedData.name()) &&
                categoryRepository.existsByNameIgnoreCase(updatedData.name())) {
            throw new AppException(CATEGORY_NAME_ALREADY_EXISTS, "Category with name: '" + updatedData.name() + "' already exists.");
        }

        // Apply changes
        categoryMapper.updateEntityFromRequest(updatedData, existingCategory);

        return categoryRepository.save(existingCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CATEGORY_NOT_FOUND));
        // Soft delete (set status to HIDDEN)
        category.setStatus(CategoryStatus.HIDDEN);

        categoryRepository.save(category);
    }
}
