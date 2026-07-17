package com.mgmtp.gives.dto.campaign_member;

import java.time.LocalDateTime;

public record UnjoinRequestResponse(
        Long userId,
        String userName,
        String userAvatarUrl,
        LocalDateTime requestedAt,
        long activeTaskCount
) {
}
