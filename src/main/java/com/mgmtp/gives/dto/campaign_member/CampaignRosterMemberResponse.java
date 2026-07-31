package com.mgmtp.gives.dto.campaign_member;

import java.time.LocalDateTime;

public record CampaignRosterMemberResponse(
        Long userId,
        String fullName,
        String avatarUrl,
        LocalDateTime joinedAt
) {
}
