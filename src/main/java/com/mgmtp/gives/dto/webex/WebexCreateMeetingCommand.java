package com.mgmtp.gives.dto.webex;

import java.time.LocalDateTime;

public record WebexCreateMeetingCommand(
        String title,
        String description,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
