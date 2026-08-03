package com.mgmtp.gives.dto.webex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebexMeetingResponse(
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
