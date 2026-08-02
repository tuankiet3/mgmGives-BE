package com.mgmtp.gives.dto.campaign_meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mgmtp.gives.enums.CampaignMeetingType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateCampaignMeetingRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        CampaignMeetingType meetingType,

        @Size(max = 1000, message = "Location must not exceed 1000 characters")
        String location,

        @Size(max = 2000, message = "Meeting URL must not exceed 2000 characters")
        String meetingUrl,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        LocalDateTime startTime,

        @NotNull(message = "End time is required")
        LocalDateTime endTime,

        @JsonProperty("notifyAll")
        Boolean notifyAllMembers,

        List<Long> recipientUserIds
) {
}
