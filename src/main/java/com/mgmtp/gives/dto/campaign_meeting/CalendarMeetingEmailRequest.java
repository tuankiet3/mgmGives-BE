package com.mgmtp.gives.dto.campaign_meeting;

import java.time.LocalDateTime;
import java.util.List;

public record CalendarMeetingEmailRequest(
        String method,
        String toEmail,
        String fullName,
        String campaignName,
        String meetingTitle,
        String meetingDescription,
        String organizerName,
        String organizerEmail,
        String meetingUrl,
        String location,
        Long campaignId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String calendarUid,
        int calendarSequence,
        List<CalendarAttendee> attendees
) {
}
