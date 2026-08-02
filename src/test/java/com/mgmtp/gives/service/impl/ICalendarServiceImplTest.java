package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.WebexProps;
import com.mgmtp.gives.dto.campaign_meeting.CalendarAttendee;
import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ICalendarServiceImplTest {
    private ICalendarServiceImpl service;

    @BeforeEach
    void setUp() {
        WebexProps webexProps = new WebexProps();
        webexProps.setTimeZone("Asia/Bangkok");
        service = new ICalendarServiceImpl(webexProps);
    }

    @Test
    void generateRequest_IncludesRequiredFieldsAndOnlineLocation() {
        String calendar = service.generate(request("REQUEST", "CONFIRMED", "https://webex.example/join", null, 0));
        String unfolded = unfold(calendar);

        assertTrue(calendar.contains("\r\n"));
        assertTrue(calendar.contains("METHOD:REQUEST\r\n"));
        assertTrue(calendar.contains("BEGIN:VEVENT\r\n"));
        assertTrue(calendar.contains("UID:meeting-uid-1\r\n"));
        assertTrue(calendar.contains("SEQUENCE:0\r\n"));
        assertTrue(calendar.contains("DTSTART:20260101T030000Z\r\n"));
        assertTrue(calendar.contains("DTEND:20260101T040000Z\r\n"));
        assertTrue(calendar.contains("SUMMARY:Kickoff\\, planning\\; review\r\n"));
        assertTrue(calendar.contains("ORGANIZER;CN=\"Campaign Host\":mailto:host@example.com\r\n"));
        assertTrue(unfolded.contains("ATTENDEE;CN=\"Alice\";ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:alice@example.com"));
        assertTrue(calendar.contains("LOCATION:https://webex.example/join\r\n"));
        assertTrue(calendar.contains("STATUS:CONFIRMED\r\n"));
        assertTrue(calendar.endsWith("END:VCALENDAR\r\n"));
    }

    @Test
    void generateCancel_IncludesCancelMethodAndCancelledStatus() {
        String calendar = service.generate(request("CANCEL", "CANCELLED", null, "Office Room 1", 2));
        String unfolded = unfold(calendar);

        assertTrue(calendar.contains("METHOD:CANCEL\r\n"));
        assertTrue(calendar.contains("SEQUENCE:2\r\n"));
        assertTrue(calendar.contains("LOCATION:Office Room 1\r\n"));
        assertTrue(calendar.contains("STATUS:CANCELLED\r\n"));
        assertTrue(calendar.contains("TRANSP:OPAQUE\r\n"));
        assertTrue(unfolded.contains("ATTENDEE;CN=\"Alice\";ROLE=REQ-PARTICIPANT;PARTSTAT=DECLINED;RSVP=FALSE:mailto:alice@example.com"));
    }

    @Test
    void generate_FoldsLongUtf8LinesAndEscapesText() {
        String calendar = service.generate(new CalendarMeetingEmailRequest(
                "REQUEST",
                "alice@example.com",
                "Alice",
                "Campaign",
                "A very long meeting title with Vietnamese text quyên góp cộng đồng and enough extra words to require line folding in the generated calendar output",
                "Line 1\nLine 2, with comma; and semicolon",
                "Campaign Host",
                "host@example.com",
                null,
                "Office",
                10L,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0),
                "meeting-uid-1",
                1,
                List.of(new CalendarAttendee("alice@example.com", "Alice"))
        ));

        assertTrue(calendar.contains("\r\n "));
        assertTrue(calendar.contains("DESCRIPTION:Line 1\\nLine 2\\, with comma\\; and semicolon"));
    }

    @Test
    void generate_UsesPlainTextDescriptionWhenMeetingDescriptionContainsEscapedHtml() {
        String calendar = service.generate(new CalendarMeetingEmailRequest(
                "REQUEST",
                "alice@example.com",
                "Alice",
                "Campaign",
                "Kickoff",
                "&lt;p&gt;Hello mgmies!&lt;/p&gt;&lt;p&gt;Support &lt;strong&gt;mgmGives&lt;/strong&gt;.&lt;/p&gt;",
                "Campaign Host",
                "host@example.com",
                "https://webex.example/join",
                "Office",
                10L,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0),
                "meeting-uid-1",
                1,
                List.of(new CalendarAttendee("alice@example.com", "Alice"))
        ));
        String unfolded = unfold(calendar);

        assertTrue(unfolded.contains("DESCRIPTION:Hello mgmies!\\nSupport mgmGives."));
        assertFalse(unfolded.contains("<p>"));
        assertFalse(unfolded.contains("&lt;p&gt;"));
        assertFalse(unfolded.contains("<strong>"));
    }

    private String unfold(String calendar) {
        return calendar.replace("\r\n ", "");
    }

    private CalendarMeetingEmailRequest request(
            String method,
            String status,
            String meetingUrl,
            String location,
            int sequence
    ) {
        return new CalendarMeetingEmailRequest(
                method,
                "alice@example.com",
                "Alice",
                "Campaign",
                "Kickoff, planning; review",
                "Discuss plan",
                "Campaign Host",
                "host@example.com",
                meetingUrl,
                location,
                10L,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0),
                "meeting-uid-1",
                sequence,
                List.of(new CalendarAttendee("alice@example.com", "Alice"))
        );
    }
}
