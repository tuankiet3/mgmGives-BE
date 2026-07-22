package com.mgmtp.gives.dto.webex;

public record WebexCreateMeetingRequest(
        String title,
        String agenda,
        String start,
        String end,
        String timezone
) {
}
