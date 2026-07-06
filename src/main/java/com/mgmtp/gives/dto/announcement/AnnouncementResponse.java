package com.mgmtp.gives.dto.announcement;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        Long campaignId,
        String title,
        String content,
        UserSummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record UserSummary(
            Long id,
            String name,
            String email
    ) {
    }
}
