package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.category.UserCategoryResponse;

import java.util.List;

/**
 * Service interface for public-facing category operations.
 * Consumed by the campaign creation flow (DANANG-1765).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #getApprovedCategories()} — public, no auth required</li>
 * </ul>
 */
public interface UserCategoryService {

    /**
     * Returns all APPROVED categories sorted by name ascending.
     * For use in the campaign creation form's category picker.
     */
    List<UserCategoryResponse> getApprovedCategories();
}
