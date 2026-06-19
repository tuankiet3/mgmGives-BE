package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.category.UserCategoryResponse;
import com.mgmtp.gives.dto.category.UserSuggestCategoryRequest;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CategoryMapper;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.service.UserCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.mgmtp.gives.common.ErrorCode.CATEGORY_NAME_ALREADY_EXISTS;

@Service
public class UserCategoryServiceImpl implements UserCategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public UserCategoryServiceImpl(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

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
     *
     * <p>Security rules enforced here:
     * <ul>
     *   <li>Status is ALWAYS set to {@link CategoryStatus#PENDING} — client cannot control it.</li>
     *   <li>Only admin-created categories (via {@code AdminCategoryService}) are set to APPROVED.</li>
     * </ul>
     */
    @Override
    @Transactional
    public UserCategoryResponse suggestCategory(UserSuggestCategoryRequest request) {
        // Normalize name: trim whitespace
        String normalizedName = request.name().trim();

        // Business rule: no duplicate names, case-insensitive
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(
                    CATEGORY_NAME_ALREADY_EXISTS,
                    "Category with name '" + normalizedName + "' already exists."
            );
        }

        // Normalize description: blank string → null
        String normalizedDescription = StringUtils.hasText(request.description())
                ? request.description().trim()
                : null;

        // Build normalized request for mapper
        UserSuggestCategoryRequest normalizedRequest = new UserSuggestCategoryRequest(
                normalizedName,
                normalizedDescription
        );

        // Security: status is always PENDING for user suggestions (enforced by @Mapping constant in CategoryMapper)
        Category saved = categoryRepository.save(categoryMapper.toEntity(normalizedRequest));

        return categoryMapper.toUserResponse(saved);
    }
}
