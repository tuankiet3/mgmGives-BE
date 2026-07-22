package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Category;
import org.springframework.data.jpa.domain.Specification;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<Category> isDeleted(Boolean showDeleted) {
        return (root, query, criteriaBuilder) -> {
            if (showDeleted == null || !showDeleted) {
                return criteriaBuilder.isNull(root.get("deletedAt"));
            }
            return criteriaBuilder.isNotNull(root.get("deletedAt"));
        };
    }

    public static Specification<Category> matchesKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.trim().isEmpty()) return null;
            String escapedKeyword = keyword
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escapedKeyword.trim().toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern, '\\')
            );
        };
    }
}
