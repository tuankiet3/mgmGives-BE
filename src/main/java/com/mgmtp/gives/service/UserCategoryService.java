package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.category.UserCategoryResponse;
import com.mgmtp.gives.dto.category.UserSuggestCategoryRequest;

import java.util.List;

/**
 * Service interface for public-facing category operations.
 * Consumed by the campaign creation flow (DANANG-1765).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #getApprovedCategories()} — public, no auth required</li>
 *   <li>{@link #suggestCategory(UserSuggestCategoryRequest)} — authenticated users only</li>
 * </ul>
 */
public interface UserCategoryService {

    /**
     * Returns all APPROVED categories sorted by name ascending.
     * For use in the campaign creation form's category picker.
     */
    List<UserCategoryResponse> getApprovedCategories();

    /**
     * Saves a user-submitted category suggestion with status PENDING.
     * Status is never accepted from the request — always forced to PENDING.
     */
    UserCategoryResponse suggestCategory(UserSuggestCategoryRequest request);
}
