package com.mgmtp.gives.dto.campaign_member;

import com.mgmtp.gives.enums.MemberListVisibility;
import jakarta.validation.constraints.NotNull;

public record UpdateRosterVisibilityRequest(
        @NotNull(message = "Visibility is required")
        MemberListVisibility visibility
) {
}
