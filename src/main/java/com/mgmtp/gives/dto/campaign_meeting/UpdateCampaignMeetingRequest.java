package com.mgmtp.gives.dto.campaign_meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateCampaignMeetingRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        LocalDateTime startTime,

        LocalDateTime endTime,

        @JsonProperty("notifyAll")
        Boolean notifyAllMembers,

        List<Long> recipientUserIds
) {
}
