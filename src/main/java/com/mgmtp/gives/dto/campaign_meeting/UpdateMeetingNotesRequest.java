package com.mgmtp.gives.dto.campaign_meeting;

import jakarta.validation.constraints.Size;

public record UpdateMeetingNotesRequest(
        @Size(max = 10000, message = "Meeting notes must not exceed 10000 characters")
        String content
) {
}
