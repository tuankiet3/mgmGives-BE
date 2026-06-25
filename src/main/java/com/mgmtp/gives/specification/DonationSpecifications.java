package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
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
}
