package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.enums.CategoryStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }


    public static Specification<Category> hasStatusIn(Collection<CategoryStatus> statuses) {
        return (root, query, criteriaBuilder) ->
                (statuses == null || statuses.isEmpty()) ? null : root.get("status").in(statuses);
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
