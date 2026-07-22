package com.mgmtp.gives.event.campaign_meeting;

public record CampaignMeetingWebexCancellationEvent(
        Long meetingId,
        String webexMeetingId,
        Long hostUserId
) {
}
