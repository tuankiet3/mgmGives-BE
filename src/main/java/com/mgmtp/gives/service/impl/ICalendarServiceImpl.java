package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.WebexProps;
import com.mgmtp.gives.dto.campaign_meeting.CalendarAttendee;
import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;
import com.mgmtp.gives.service.ICalendarService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ICalendarServiceImpl implements ICalendarService {
    private static final String CRLF = "\r\n";
    private static final int FOLD_LIMIT_BYTES = 75;
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final WebexProps webexProps;
    private final Clock clock = Clock.systemUTC();

    @Override
    public String generate(CalendarMeetingEmailRequest request) {
        String method = normalizeMethod(request.method());
        String status = "CANCEL".equals(method) ? "CANCELLED" : "CONFIRMED";
        String location = displayLocation(request);

        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//mgmGives//Campaign Meetings//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:" + method);
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + escapeText(request.calendarUid()));
        lines.add("SEQUENCE:" + request.calendarSequence());
        lines.add("DTSTAMP:" + UTC_FORMATTER.format(clock.instant().atZone(ZoneId.of("UTC"))));
        lines.add("LAST-MODIFIED:" + UTC_FORMATTER.format(clock.instant().atZone(ZoneId.of("UTC"))));
        lines.add("DTSTART:" + formatDateTime(request.startTime()));
        lines.add("DTEND:" + formatDateTime(request.endTime()));
        lines.add("SUMMARY:" + escapeText(request.meetingTitle()));
        lines.add("DESCRIPTION:" + escapeText(description(request)));
        lines.add("LOCATION:" + escapeText(location));
        lines.add("STATUS:" + status);
        lines.add("TRANSP:OPAQUE");
        lines.add(organizerLine(request));
        attendeeLines(request, method).forEach(lines::add);
        if (StringUtils.hasText(request.meetingUrl())) {
            lines.add("URL:" + escapeText(request.meetingUrl()));
        }
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        return foldLines(lines);
    }

    private String normalizeMethod(String method) {
        return "CANCEL".equalsIgnoreCase(method) ? "CANCEL" : "REQUEST";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        ZoneId sourceZone = ZoneId.of(webexProps.getTimeZone());
        return UTC_FORMATTER.format(dateTime.atZone(sourceZone).withZoneSameInstant(ZoneId.of("UTC")));
    }

    private String description(CalendarMeetingEmailRequest request) {
        List<String> parts = new ArrayList<>();
        String plainDescription = htmlToPlainText(request.meetingDescription());
        if (StringUtils.hasText(plainDescription)) {
            parts.add(plainDescription);
        }
        if (StringUtils.hasText(request.campaignName())) {
            parts.add("Campaign: " + request.campaignName());
        }
        if (StringUtils.hasText(request.meetingUrl())) {
            parts.add("Webex: " + request.meetingUrl());
        }
        return String.join("\n\n", parts);
    }

    private String htmlToPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }

        String htmlWithLineBreaks = Parser.unescapeEntities(html, false)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n");
        String plainText = Jsoup.parse(htmlWithLineBreaks)
                .wholeText()
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return plainText.isEmpty() ? null : plainText;
    }

    private String displayLocation(CalendarMeetingEmailRequest request) {
        if (StringUtils.hasText(request.location())) {
            return request.location();
        }
        return StringUtils.hasText(request.meetingUrl()) ? request.meetingUrl() : "";
    }

    private String organizerLine(CalendarMeetingEmailRequest request) {
        String email = StringUtils.hasText(request.organizerEmail()) ? request.organizerEmail().trim() : "";
        String name = StringUtils.hasText(request.organizerName()) ? request.organizerName().trim() : email;
        return "ORGANIZER;CN=" + escapeParam(name) + ":mailto:" + email;
    }

    private List<String> attendeeLines(CalendarMeetingEmailRequest request, String method) {
        if (request.attendees() == null) {
            return List.of();
        }
        return request.attendees()
                .stream()
                .filter(attendee -> attendee != null && StringUtils.hasText(attendee.email()))
                .map(attendee -> attendeeLine(attendee, method))
                .toList();
    }

    private String attendeeLine(CalendarAttendee attendee, String method) {
        String email = attendee.email().trim();
        String name = StringUtils.hasText(attendee.fullName()) ? attendee.fullName().trim() : email;
        String partStatus = "CANCEL".equals(method) ? "DECLINED" : "NEEDS-ACTION";
        String rsvp = "CANCEL".equals(method) ? "FALSE" : "TRUE";
        return "ATTENDEE;CN=" + escapeParam(name)
                + ";ROLE=REQ-PARTICIPANT;PARTSTAT=" + partStatus + ";RSVP=" + rsvp + ":mailto:" + email;
    }

    private String escapeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    private String escapeParam(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
        return "\"" + escaped + "\"";
    }

    private String foldLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(foldLine(line)).append(CRLF);
        }
        return builder.toString();
    }

    private String foldLine(String line) {
        StringBuilder folded = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            String value = new String(Character.toChars(codePoint));
            int valueBytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes > 0 && currentBytes + valueBytes > FOLD_LIMIT_BYTES) {
                folded.append(current).append(CRLF).append(" ");
                current.setLength(0);
                currentBytes = 1;
            }
            current.append(value);
            currentBytes += valueBytes;
            offset += Character.charCount(codePoint);
        }

        folded.append(current);
        return folded.toString();
    }
}
