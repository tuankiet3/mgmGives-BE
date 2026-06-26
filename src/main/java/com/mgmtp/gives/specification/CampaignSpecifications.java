package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
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

    public static Specification<Campaign> hasCategory(Long categoryId) {
        return (root, query, cb) -> {
            if (categoryId == null)
                return null;
            return cb.equal(root.join("categories").get("id"), categoryId);
        };
    }

    public static Specification<Campaign> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty())
                return null;
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.join("user").get("fullName")), pattern));
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
                // Admins see all campaigns
                return cb.conjunction();
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
}
