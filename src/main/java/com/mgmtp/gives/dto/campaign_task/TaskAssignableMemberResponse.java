package com.mgmtp.gives.dto.campaign_task;

import com.mgmtp.gives.enums.CampaignMemberRole;

public record TaskAssignableMemberResponse(
        Long id,
        String fullName,
        String email,
        String avatarUrl,
        CampaignMemberRole roleInCampaign
) {
}
