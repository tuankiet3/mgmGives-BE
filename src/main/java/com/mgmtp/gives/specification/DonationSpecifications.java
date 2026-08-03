package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class DonationSpecifications {

    private DonationSpecifications() {
        // Utility class pattern
    }

    public static Specification<Donation> hasStatus(DonationStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Donation> hasType(DonationType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Donation> hasCampaignId(Long campaignId) {
        return (root, query, cb) -> campaignId == null ? null : cb.equal(root.get("campaign").get("id"), campaignId);
    }

    public static Specification<Donation> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Donation> isAnonymous(Boolean anonymous) {
        return (root, query, cb) -> anonymous == null ? null : cb.equal(root.get("isAnonymous"), anonymous);
    }

    public static Specification<Donation> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.trim().isEmpty()) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            Join<Donation, User> userJoin = root.join("user", JoinType.LEFT);
            Join<Donation, Campaign> campaignJoin = root.join("campaign", JoinType.LEFT);
            
            return cb.or(
                cb.like(cb.lower(userJoin.get("fullName")), pattern),
                cb.like(cb.lower(campaignJoin.get("title")), pattern),
                cb.like(cb.lower(root.get("transactionId")), pattern),
                cb.like(cb.lower(root.get("transactionDescription")), pattern)
            );
        };
    }
}
