package com.mgmtp.gives.event.campaign_meeting;

public record CampaignMeetingCancellationEmailEvent(
        String toEmail,
        String fullName,
        String campaignName,
        String meetingTitle,
        String meetingDescription,
        String createdByName,
        String startTime,
        String endTime
) {
}
