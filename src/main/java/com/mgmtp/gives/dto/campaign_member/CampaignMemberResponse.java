package com.mgmtp.gives.dto.campaign_member;

import java.time.LocalDateTime;

public record CampaignMemberResponse(
        Long id,
        Long campaignId,
        Long userId,
        String role,
        LocalDateTime joinedAt
) {
}