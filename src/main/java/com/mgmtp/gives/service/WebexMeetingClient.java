package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.webex.WebexCreateMeetingCommand;
import com.mgmtp.gives.dto.webex.WebexMeetingResult;

public interface WebexMeetingClient {
    WebexMeetingResult createMeeting(WebexCreateMeetingCommand command, String accessToken);

    WebexMeetingResult updateMeeting(String meetingId, WebexCreateMeetingCommand command, String accessToken);

    WebexMeetingResult getMeeting(String meetingId, String accessToken);

    void cancelMeeting(String meetingId, String accessToken);
}
