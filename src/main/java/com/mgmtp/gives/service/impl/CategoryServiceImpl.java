package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.category.*;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CategoryMapper;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.service.AdminCategoryService;
import com.mgmtp.gives.service.UserCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mgmtp.gives.util.StringNormalizeUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NAME_ALREADY_EXISTS;
import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NOT_FOUND;
import static com.mgmtp.gives.common.ErrorCode.VALIDATION_ERROR;
import static com.mgmtp.gives.specification.CategorySpecifications.isDeleted;
import static com.mgmtp.gives.specification.CategorySpecifications.matchesKeyword;

@Service @RequiredArgsConstructor @Slf4j
public class CategoryServiceImpl implements UserCategoryService, AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final CampaignRepository campaignRepository;

    /**
     * Common helper method to validate category name uniqueness (case-insensitive).
     * Normalises the name and validates that it is not blank.
     */
    private String normaliseAndValidateUniqueName(String name) {
        String normalisedName = StringNormalizeUtils.normalizeName(name);

        if (normalisedName == null || normalisedName.isEmpty()) {
            log.warn("Category name validation failed: blank name");
            throw new AppException(
                    VALIDATION_ERROR,
                    "Category name must not be blank."
            );
        }

        if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(normalisedName)) {
            log.warn("Category name rejected: already exists. name={}", normalisedName);
            throw new AppException(
                    CATEGORY_NAME_ALREADY_EXISTS,
                    "Category with name '" + normalisedName + "' already exists."
            );
        }

        Optional<Category> existingOpt = categoryRepository.findByNameIgnoreCase(normalisedName);
        if (existingOpt.isPresent() && existingOpt.get().getDeletedAt() != null) {
            log.warn("Category name collision with archived category. name={}", normalisedName);
            throw new AppException(
                    ErrorCode.CATEGORY_ALREADY_EXISTS_BUT_DELETED,
                    "Category with name '" + normalisedName + "' already exists in archives.",
                    java.util.Map.of("id", existingOpt.get().getId())
            );
        }

        return normalisedName;
    }

    // =========================================================================
    // UserCategoryService Implementation
    // =========================================================================

    /**
     * Returns all active categories sorted by name ascending.
     * This is the public-facing list used by the campaign creation flow (DANANG-1765).
     * Read-only transaction — no DB writes occur here.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserCategoryResponse> getApprovedCategories() {
        List<Category> categories = categoryRepository.findAllByDeletedAtIsNullOrderByNameAsc();
        return categoryMapper.toUserResponseList(categories);
    }
    // AdminCategoryService Implementation
    // =========================================================================

    /**
     * Handles the creation of a new category with a business check for duplicate names.
     */
    @Override
    @Transactional
    public AdminCategoryResponse createCategory(AdminCreateCategoryRequest request) {
        // Normalise and check uniqueness
        String normalisedName = normaliseAndValidateUniqueName(request.name());
        String normalisedDescription = StringNormalizeUtils.normalizeDescription(request.description());

        AdminCreateCategoryRequest normalisedRequest = new AdminCreateCategoryRequest(
                normalisedName,
                normalisedDescription
        );

        Category category = categoryMapper.toEntity(normalisedRequest);
        Category saved = categoryRepository.save(category);

        log.info("Category created by admin: id={}, name={}", saved.getId(), saved.getName());

        return categoryMapper.toAdminResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminCategoryResponse> getAllCategories(boolean showDeleted, String search, Pageable pageable) {
        Specification<Category> spec = Specification.allOf(
                isDeleted(showDeleted),
                matchesKeyword(search)
        );

        Page<Category> categoryPage = categoryRepository.findAll(spec, pageable);
        return categoryPage.map(categoryMapper::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Category not found: id={}", id);
                    return new AppException(CATEGORY_NOT_FOUND);
                });
        return categoryMapper.toAdminResponse(category);
    }

    @Override
    @Transactional
    public AdminCategoryResponse updateCategory(Long id, AdminUpdateCategoryRequest updatedData) {
        // Blow up if not found
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update category failed: not found. id={}", id);
                    return new AppException(CATEGORY_NOT_FOUND, "Category not found with id=" + id);
                });

        // Normalise the name from request
        String normalisedName = StringNormalizeUtils.normalizeName(updatedData.name());
        if (normalisedName == null || normalisedName.isEmpty()) {
            throw new AppException(VALIDATION_ERROR, "Category name must not be blank.");
        }

        // Business logic: avoid duplicate when renaming
        if (!existingCategory.getName().equalsIgnoreCase(normalisedName)) {
            if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(normalisedName)) {
                log.warn("Update category rejected: new name already exists. id={}, newName={}", id, normalisedName);
                throw new AppException(
                        CATEGORY_NAME_ALREADY_EXISTS,
                        "Category with name '" + normalisedName + "' already exists."
                );
            }

            Optional<Category> existingOpt = categoryRepository.findByNameIgnoreCase(normalisedName);
            if (existingOpt.isPresent() && existingOpt.get().getDeletedAt() != null) {
                log.warn("Update category rejected: new name exists in archives. id={}, newName={}", id, normalisedName);
                throw new AppException(
                        ErrorCode.CATEGORY_ALREADY_EXISTS_BUT_DELETED,
                        "Category with name '" + normalisedName + "' already exists in archives.",
                        java.util.Map.of("id", existingOpt.get().getId())
                );
            }
        }

        // Normalise description
        String normalisedDescription = StringNormalizeUtils.normalizeDescription(updatedData.description());

        AdminUpdateCategoryRequest normalisedRequest = new AdminUpdateCategoryRequest(
                normalisedName,
                normalisedDescription
        );

        // Apply changes
        categoryMapper.updateEntityFromRequest(normalisedRequest, existingCategory);

        Category saved = categoryRepository.save(existingCategory);
        log.info("Category updated: id={}, name={}", saved.getId(), saved.getName());
        return categoryMapper.toAdminResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> {
                    log.warn("Delete category failed: not found. id={}", id);
                    return new AppException(CATEGORY_NOT_FOUND);
                });

        category.setDeletedAt(LocalDateTime.now());
        categoryRepository.save(category);
        log.info("Category soft-deleted: id={}, name={}", id, category.getName());
    }

    @Override
    @Transactional
    public AdminCategoryResponse restoreCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Restore category failed: not found. id={}", id);
                    return new AppException(CATEGORY_NOT_FOUND);
                });

        if (categoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(category.getName())) {
            log.warn("Restore category failed: name already exists as active. id={}, name={}", id, category.getName());
            throw new AppException(
                    CATEGORY_NAME_ALREADY_EXISTS,
                    "An active category with the name '" + category.getName() + "' already exists. Cannot restore."
            );
        }

        category.setDeletedAt(null);
        Category saved = categoryRepository.save(category);
        log.info("Category restored: id={}, name={}", id, category.getName());
        return categoryMapper.toAdminResponse(saved);
    }

    @Override
    @Transactional
    public void permanentDeleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Permanent delete category failed: not found. id={}", id);
                    return new AppException(CATEGORY_NOT_FOUND);
                });

        if (category.getDeletedAt() == null) {
            log.warn("Permanent delete category failed: category is not archived. id={}", id);
            throw new AppException(VALIDATION_ERROR, "Only archived categories can be permanently deleted.");
        }

        long assignedCampaigns = campaignRepository.countCampaignsByCategoryId(id);
        if (assignedCampaigns > 0) {
            log.warn("Permanent delete category failed: category is assigned to {} campaigns. id={}", assignedCampaigns, id);
            throw new AppException(
                    VALIDATION_ERROR,
                    "Cannot permanently delete this category because it is assigned to " + assignedCampaigns + " campaigns. Please remove it from those campaigns first."
            );
        }

        categoryRepository.delete(category);
        log.info("Category permanently deleted: id={}, name={}", id, category.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryDeleteCheckResponse checkCategoryDeletion(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new AppException(CATEGORY_NOT_FOUND);
        }
        long assigned = campaignRepository.countCampaignsByCategoryId(id);
        long onlyCategory = campaignRepository.countCampaignsWhereCategoryIsOnlyOne(id);
        return new CategoryDeleteCheckResponse(assigned, onlyCategory);
    }
}
