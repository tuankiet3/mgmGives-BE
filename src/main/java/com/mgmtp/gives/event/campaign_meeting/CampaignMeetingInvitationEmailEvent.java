package com.mgmtp.gives.event.campaign_meeting;

public record CampaignMeetingInvitationEmailEvent(
        String toEmail,
        String fullName,
        String campaignName,
        String meetingTitle,
        String meetingDescription,
        String createdByName,
        String meetingUrl,
        Long campaignId,
        String startTime,
        String endTime
) {
}
