package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.WebexProps;
import com.mgmtp.gives.dto.webex.WebexCreateMeetingCommand;
import com.mgmtp.gives.dto.webex.WebexCreateMeetingRequest;
import com.mgmtp.gives.dto.webex.WebexMeetingResponse;
import com.mgmtp.gives.dto.webex.WebexMeetingResult;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.service.WebexMeetingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class WebexMeetingClientImpl implements WebexMeetingClient {
    private final WebexProps webexProps;

    @Override
    public WebexMeetingResult createMeeting(WebexCreateMeetingCommand command, String accessToken) {
        WebexCreateMeetingRequest request = new WebexCreateMeetingRequest(
                command.title(),
                command.description(),
                formatWebexDateTime(command.startTime()),
                formatWebexDateTime(command.endTime()),
                webexProps.getTimeZone()
        );

        try {
            WebexMeetingResponse response = RestClient.create(webexProps.getApiBaseUrl())
                    .post()
                    .uri("/meetings")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(WebexMeetingResponse.class);

            if (response == null || !StringUtils.hasText(response.id()) || !StringUtils.hasText(response.webLink())) {
                throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Webex did not return a valid meeting link");
            }

            return new WebexMeetingResult(
                    response.id(),
                    response.webLink(),
                    response.title(),
                    response.start(),
                    response.end(),
                    response.timezone()
            );
        } catch (RestClientResponseException ex) {
            throw new AppException(
                    ErrorCode.UNCATEGORIZED_ERROR,
                    "Failed to create Webex meeting: " + ex.getResponseBodyAsString()
            );
        }
    }

    @Override
    public WebexMeetingResult updateMeeting(String meetingId, WebexCreateMeetingCommand command, String accessToken) {
        if (!StringUtils.hasText(meetingId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Webex meeting ID is required");
        }

        WebexCreateMeetingRequest request = new WebexCreateMeetingRequest(
                command.title(),
                command.description(),
                formatWebexDateTime(command.startTime()),
                formatWebexDateTime(command.endTime()),
                webexProps.getTimeZone()
        );

        try {
            WebexMeetingResponse response = RestClient.create(webexProps.getApiBaseUrl())
                    .put()
                    .uri("/meetings/{meetingId}", meetingId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(WebexMeetingResponse.class);

            if (response == null || !StringUtils.hasText(response.id()) || !StringUtils.hasText(response.webLink())) {
                throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Webex did not return a valid updated meeting");
            }

            return new WebexMeetingResult(
                    response.id(),
                    response.webLink(),
                    response.title(),
                    response.start(),
                    response.end(),
                    response.timezone()
            );
        } catch (RestClientResponseException ex) {
            throw new AppException(
                    ErrorCode.UNCATEGORIZED_ERROR,
                    "Failed to update Webex meeting: " + ex.getResponseBodyAsString()
            );
        }
    }

    @Override
    public void cancelMeeting(String meetingId, String accessToken) {
        if (!StringUtils.hasText(meetingId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Webex meeting ID is required");
        }

        try {
            RestClient.create(webexProps.getApiBaseUrl())
                    .delete()
                    .uri("/meetings/{meetingId}", meetingId)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new AppException(
                    ErrorCode.UNCATEGORIZED_ERROR,
                    "Failed to cancel Webex meeting: " + ex.getResponseBodyAsString()
            );
        }
    }

    private String formatWebexDateTime(LocalDateTime dateTime) {
        ZoneId zoneId = ZoneId.of(webexProps.getTimeZone());
        return dateTime.atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
