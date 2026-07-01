package com.mgmtp.gives.dto.campaign_meeting;

import java.time.LocalDateTime;

public record MeetingActivityResponse(
        String type,
        String message,
        Long actorId,
        String actorName,
        LocalDateTime timestamp
) {
}
