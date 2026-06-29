package com.mgmtp.gives.enums;

import java.util.Set;

public enum CampaignStatus {
    DRAFT,
    PENDING,
    APPROVED,
    IN_PROGRESS,
    REJECTED,
    COMPLETED;

    private static final Set<CampaignStatus> EDITABLE_STATUSES = Set.of(DRAFT, PENDING, REJECTED);

    public boolean isEditable() {
        return EDITABLE_STATUSES.contains(this);
    }
}
