package com.mgmtp.gives.dto.campaign_meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mgmtp.gives.enums.CampaignMeetingType;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateCampaignMeetingRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        String description,

        CampaignMeetingType meetingType,

        @Size(max = 1000, message = "Location must not exceed 1000 characters")
        String location,

        LocalDateTime startTime,

        LocalDateTime endTime,

        @JsonProperty("notifyAll")
        Boolean notifyAllMembers,

        List<Long> recipientUserIds
) {
}
