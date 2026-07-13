package com.mgmtp.gives.dto.webex;

public record WebexMeetingResult(
        String id,
        String webLink,
        String title,
        String start,
        String end,
        String timezone,
        String state,
        String meetingType
) {
}
