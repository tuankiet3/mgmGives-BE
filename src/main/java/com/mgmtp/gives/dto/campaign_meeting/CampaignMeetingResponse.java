package com.mgmtp.gives.dto.campaign_meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CampaignMeetingResponse(
        Long id,
        Long campaignId,
        Long createdById,
        String createdByName,
        String webexMeetingId,
        String title,
        String description,
        String meetingUrl,
        LocalDateTime startTime,
        LocalDateTime endTime,
        CampaignMeetingStatus status,
        @JsonProperty("notifyAll")
        Boolean notifyAllMembers,
        Integer invitedCount,
        List<Long> invitedUserIds,
        String displayStatus,
        Boolean canManage,
        Boolean canUpdate,
        Boolean canCancel,
        Boolean canEditNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
