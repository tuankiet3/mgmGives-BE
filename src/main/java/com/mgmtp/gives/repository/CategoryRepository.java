package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Checks if a category name already exists in the database, ignoring case sensitivity.
     * This directly supports our validation requirement to prevent duplicates.
     */
    boolean existsByNameIgnoreCase(String name);

    Page<Category> findAllByStatusIn(Collection<CategoryStatus> statuses, Pageable pageable);
}
