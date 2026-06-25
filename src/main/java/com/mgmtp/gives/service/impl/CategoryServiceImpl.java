package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.category.*;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CategoryMapper;
import com.mgmtp.gives.repository.CategoryRepository;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NAME_ALREADY_EXISTS;
import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NOT_FOUND;
import static com.mgmtp.gives.common.ErrorCode.VALIDATION_ERROR;
import static com.mgmtp.gives.specification.CategorySpecifications.hasStatusIn;
import static com.mgmtp.gives.specification.CategorySpecifications.matchesKeyword;

@Service @RequiredArgsConstructor @Slf4j
public class CategoryServiceImpl implements UserCategoryService, AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

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

        if (categoryRepository.existsByNameIgnoreCase(normalisedName)) {
            log.warn("Category name rejected: already exists. name={}", normalisedName);
            throw new AppException(
                    CATEGORY_NAME_ALREADY_EXISTS,
                    "Category with name '" + normalisedName + "' already exists."
            );
        }

        return normalisedName;
    }

    // =========================================================================
    // UserCategoryService Implementation
    // =========================================================================

    /**
     * Returns all APPROVED categories sorted by name ascending.
     * This is the public-facing list used by the campaign creation flow (DANANG-1765).
     * Read-only transaction — no DB writes occur here.
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserCategoryResponse> getApprovedCategories() {
        List<Category> categories = categoryRepository.findAllByStatusOrderByNameAsc(CategoryStatus.APPROVED);
        return categoryMapper.toUserResponseList(categories);
    }

    /**
     * Persists a new category suggestion submitted by an authenticated user.
     * Enforces status forced to PENDING.
     */
    @Override
    @Transactional
    public UserCategoryResponse suggestCategory(UserSuggestCategoryRequest request) {

        String normalisedName = StringNormalizeUtils.normalizeName(request.name());
        if (normalisedName == null || normalisedName.isEmpty()) {
            throw new AppException(
                    VALIDATION_ERROR,
                    "Category name must not be blank."
            );
        }

        // Return existing category if found (regardless of status PENDING or APPROVED)
        // If it was REJECTED or HIDDEN, reset it to PENDING for admin review again
        Optional<Category> existingOpt = categoryRepository.findByNameIgnoreCase(normalisedName);
        if (existingOpt.isPresent()) {
            Category existing = existingOpt.get();
            if (existing.getStatus() == CategoryStatus.REJECTED || existing.getStatus() == CategoryStatus.HIDDEN) {
                CategoryStatus oldStatus = existing.getStatus();
                existing.setStatus(CategoryStatus.PENDING);
                Category saved = categoryRepository.save(existing);
                log.info("Existing category (previously {}) suggested again, auto-transitioned back to PENDING. id={}, name={}",
                        oldStatus, saved.getId(), saved.getName());
                return categoryMapper.toUserResponse(saved);
            }
            return categoryMapper.toUserResponse(existing);
        }

        // Normalize description: blank string -> null
        String normalizedDescription = StringNormalizeUtils.normalizeDescription(request.description());

        // Build normalized request for mapper
        UserSuggestCategoryRequest normalizedRequest = new UserSuggestCategoryRequest(
                normalisedName,
                normalizedDescription
        );

        // Security: status is always PENDING for user suggestions (enforced by @Mapping constant in CategoryMapper)
        Category saved = categoryRepository.save(categoryMapper.toEntity(normalizedRequest));
        log.info("Category suggestion created: id={}, name={}, status=PENDING", saved.getId(), saved.getName());

        return categoryMapper.toUserResponse(saved);
    }

    // =========================================================================
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
    public Page<AdminCategoryResponse> getAllCategories(Collection<CategoryStatus> statuses, String search, Pageable pageable) {
        Specification<Category> spec = Specification.allOf(
                hasStatusIn(statuses),
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
            if (categoryRepository.existsByNameIgnoreCase(normalisedName)) {
                log.warn("Update category rejected: new name already exists. id={}, newName={}", id, normalisedName);
                throw new AppException(
                        CATEGORY_NAME_ALREADY_EXISTS,
                        "Category with name '" + normalisedName + "' already exists."
                );
            }
        }

        // Normalise description
        String normalisedDescription = StringNormalizeUtils.normalizeDescription(updatedData.description());

        AdminUpdateCategoryRequest normalisedRequest = new AdminUpdateCategoryRequest(
                normalisedName,
                normalisedDescription,
                updatedData.status()
        );

        // Apply changes
        categoryMapper.updateEntityFromRequest(normalisedRequest, existingCategory);

        Category saved = categoryRepository.save(existingCategory);
        log.info("Category updated: id={}, name={}, status={}", saved.getId(), saved.getName(), saved.getStatus());
        return categoryMapper.toAdminResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete category failed: not found. id={}", id);
                    return new AppException(CATEGORY_NOT_FOUND);
                });

        // Soft delete (set status to HIDDEN)
        category.setStatus(CategoryStatus.HIDDEN);

        categoryRepository.save(category);
        log.info("Category soft-deleted (HIDDEN): id={}, name={}", id, category.getName());
    }
}
