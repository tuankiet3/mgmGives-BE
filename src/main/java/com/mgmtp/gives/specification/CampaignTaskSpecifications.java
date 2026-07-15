package com.mgmtp.gives.specification;

import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.enums.TaskStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public final class CampaignTaskSpecifications {

    private CampaignTaskSpecifications() {
        // Utility class pattern
    }

    public static Specification<CampaignTask> hasCampaignId(Long campaignId) {
        return (root, query, cb) -> campaignId == null ? null : cb.equal(root.get("campaign").get("id"), campaignId);
    }

    public static Specification<CampaignTask> hasStatus(TaskStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<CampaignTask> hasAssigneeId(Long assigneeId) {
        return (root, query, cb) -> {
            if (assigneeId == null) return null;
            Join<CampaignTask, TaskAssignment> assignments = root.join("assignments");
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.distinct(true);
            }
            return cb.equal(assignments.get("user").get("id"), assigneeId);
        };
    }

    public static Specification<CampaignTask> hasIsArchived(Boolean isArchived) {
        return (root, query, cb) -> isArchived == null ? null : cb.equal(root.get("isArchived"), isArchived);
    }

    public static Specification<CampaignTask> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<CampaignTask> isDeleted() {
        return (root, query, cb) -> cb.isNotNull(root.get("deletedAt"));
    }
}
