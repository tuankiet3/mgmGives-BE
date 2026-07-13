package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

public final class CampaignSpecifications {

    private CampaignSpecifications() {
        // Utility class pattern
    }

    public static Specification<Campaign> hasStatus(CampaignStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Campaign> hasPriority(CampaignPriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Campaign> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Campaign> hasCategories(List<Long> categoryIds) {
        return (root, query, cb) -> {
            if (categoryIds == null || categoryIds.isEmpty())
                return null;

            List<Long> distinctIds = categoryIds.stream().distinct().toList();

            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.distinct(true);
            }

            jakarta.persistence.criteria.Predicate[] predicates = new jakarta.persistence.criteria.Predicate[distinctIds.size()];
            for (int i = 0; i < distinctIds.size(); i++) {
                predicates[i] = cb.equal(root.join("categories").get("id"), distinctIds.get(i));
            }
            return cb.and(predicates);
        };
    }

    public static Specification<Campaign> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty())
                return null;
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("title")), pattern);
        };
    }

    public static Specification<Campaign> matchesTitleOrOrganizerKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty())
                return null;
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("user").get("fullName")), pattern));
        };
    }

    public static Specification<Campaign> isVisibleTo(User currentUser) {
        return (root, query, cb) -> {
            if (currentUser == null) {
                // In case security filter gets bypassed or for test cases
                return root.get("status").in(
                        CampaignStatus.APPROVED,
                        CampaignStatus.IN_PROGRESS,
                        CampaignStatus.COMPLETED);
            }
            if (currentUser.getRole() == UserRole.ADMIN) {
                // Admins see all campaigns except drafts of other users
                return cb.or(
                        cb.notEqual(root.get("status"), CampaignStatus.DRAFT),
                        cb.equal(root.get("user").get("id"), currentUser.getId()));
            }
            // Normal users see APPROVED, IN_PROGRESS, COMPLETED campaigns OR their own
            // campaigns
            return cb.or(
                    root.get("status").in(
                            CampaignStatus.APPROVED,
                            CampaignStatus.IN_PROGRESS,
                            CampaignStatus.COMPLETED),
                    cb.equal(root.get("user").get("id"), currentUser.getId()));
        };
    }

    public static Specification<Campaign> isNotFollowedBy(User currentUser) {
        return (root, query, cb) -> {
            if (currentUser == null) {
                return null;
            }
            jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
            jakarta.persistence.criteria.Root<CampaignFollower> subRoot = subquery.from(CampaignFollower.class);
            subquery.select(subRoot.get("campaign").get("id"));
            subquery.where(cb.and(
                    cb.equal(subRoot.get("campaign").get("id"), root.get("id")),
                    cb.equal(subRoot.get("user").get("id"), currentUser.getId())));
            return cb.not(cb.exists(subquery));
        };
    }

    public static Specification<Campaign> isFollowedBy(User currentUser) {
        return (root, query, cb) -> {
            if (currentUser == null) {
                return cb.disjunction();
            }
            jakarta.persistence.criteria.Subquery<Long> subquery = query.subquery(Long.class);
            jakarta.persistence.criteria.Root<CampaignFollower> subRoot = subquery.from(CampaignFollower.class);
            subquery.select(subRoot.get("campaign").get("id"));
            subquery.where(cb.and(
                    cb.equal(subRoot.get("campaign").get("id"), root.get("id")),
                    cb.equal(subRoot.get("user").get("id"), currentUser.getId())));
            return cb.exists(subquery);
        };
    }
}
