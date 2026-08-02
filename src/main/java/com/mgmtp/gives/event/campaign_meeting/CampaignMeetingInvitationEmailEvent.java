package com.mgmtp.gives.event.campaign_meeting;

import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;

public record CampaignMeetingInvitationEmailEvent(
        CalendarMeetingEmailRequest request
) {
}
