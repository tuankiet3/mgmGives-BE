package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;

public interface ICalendarService {
    String generate(CalendarMeetingEmailRequest request);
}
