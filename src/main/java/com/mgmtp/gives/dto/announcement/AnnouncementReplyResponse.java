package com.mgmtp.gives.dto.announcement;

import java.time.LocalDateTime;

public record AnnouncementReplyResponse(
        Long id,
        Long announcementId,
        String content,
        UserSummary createdBy,
        boolean isEdited,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record UserSummary(
            Long id,
            String name,
            String avatarUrl
    ) {
    }
}
