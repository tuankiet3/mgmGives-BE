package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> hasRoleIn(Collection<UserRole> roles) {
        return (root, query, criteriaBuilder) ->
                (roles == null || roles.isEmpty()) ? null : root.get("role").in(roles);
    }

    public static Specification<User> hasStatusIn(Collection<UserStatus> statuses) {
        return (root, query, criteriaBuilder) ->
                (statuses == null || statuses.isEmpty()) ? null : root.get("status").in(statuses);
    }

    public static Specification<User> matchesKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.trim().isEmpty()) return null;
            String escapedKeyword = keyword
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escapedKeyword.trim().toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), pattern, '\\'),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern, '\\')
            );
        };
    }
}
