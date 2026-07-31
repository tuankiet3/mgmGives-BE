package com.mgmtp.gives.dto.campaign_member;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberHiddenRequest(
        @NotNull(message = "Hidden flag is required")
        Boolean hidden
) {
}
