package com.mgmtp.gives.dto.campaign_meeting;

import java.time.LocalDateTime;

public record MeetingNotesResponse(
        Long meetingId,
        String content,
        LocalDateTime updatedAt,
        Long updatedById,
        String updatedByName,
        boolean canEdit
) {
}
