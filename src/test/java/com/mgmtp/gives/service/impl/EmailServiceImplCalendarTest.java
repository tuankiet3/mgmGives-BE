package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign_meeting.CalendarAttendee;
import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;
import com.mgmtp.gives.service.ICalendarService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceImplCalendarTest {
    private JavaMailSender mailSender;
    private TemplateEngine templateEngine;
    private ICalendarService iCalendarService;
    private EmailServiceImpl emailService;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        MailProps mailProps = new MailProps();
        mailProps.setFromMail("mgm.gives@example.com");
        mailProps.setFrontendUrl("https://mgmgives.example");
        mailSender = mock(JavaMailSender.class);
        templateEngine = mock(TemplateEngine.class);
        iCalendarService = mock(ICalendarService.class);
        BrevoEmailClient brevoEmailClient = mock(BrevoEmailClient.class);
        emailService = new EmailServiceImpl(mailProps, mailSender, brevoEmailClient, templateEngine, iCalendarService);
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html>meeting</html>");
        when(iCalendarService.generate(any(CalendarMeetingEmailRequest.class))).thenReturn("BEGIN:VCALENDAR\r\nMETHOD:REQUEST\r\nEND:VCALENDAR\r\n");
    }

    @Test
    void sendCampaignMeetingCalendarEmail_SendsMultipartRequestCalendarMessage() throws Exception {
        emailService.sendCampaignMeetingCalendarEmail(request("REQUEST"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertEquals("urn:content-classes:calendarmessage", sent.getHeader("Content-Class", null));
        assertTrue(sent.getContentType().toLowerCase().contains("multipart"));

        MimeMultipart multipart = (MimeMultipart) sent.getContent();
        assertEquals(3, multipart.getCount());
        assertTrue(multipart.getBodyPart(0).getContentType().toLowerCase().startsWith("text/plain"));
        assertTrue(multipart.getBodyPart(1).getContentType().toLowerCase().startsWith("text/html"));
        assertTrue(multipart.getBodyPart(2).getContentType().toLowerCase().contains("text/calendar"));
        assertTrue(multipart.getBodyPart(2).getContentType().toLowerCase().contains("method=request"));
        assertTrue(multipart.getBodyPart(2).getContentType().toLowerCase().contains("component=vevent"));
        verify(templateEngine).process(eq("campaign-meeting-invitation"), any(Context.class));
    }

    @Test
    void sendCampaignMeetingCalendarEmail_SendsCancelMethodForCancellation() throws Exception {
        when(iCalendarService.generate(any(CalendarMeetingEmailRequest.class))).thenReturn("BEGIN:VCALENDAR\r\nMETHOD:CANCEL\r\nEND:VCALENDAR\r\n");

        emailService.sendCampaignMeetingCalendarEmail(request("CANCEL"));

        MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
        assertTrue(multipart.getBodyPart(2).getContentType().toLowerCase().contains("method=cancel"));
        assertTrue(multipart.getBodyPart(2).getContentType().toLowerCase().contains("component=vevent"));
        verify(templateEngine).process(eq("campaign-meeting-cancellation"), any(Context.class));
    }

    @Test
    void sendCampaignMeetingCalendarEmail_UsesSanitizedHtmlDescriptionAndPlainTextFallback() throws Exception {
        String htmlDescription = """
                &lt;p&gt;Hello mgmies!&lt;/p&gt;
                &lt;p&gt;Support &lt;strong&gt;mgmGives&lt;/strong&gt;.&lt;/p&gt;
                &lt;script&gt;alert('xss')&lt;/script&gt;
                """;

        emailService.sendCampaignMeetingCalendarEmail(request("REQUEST", htmlDescription));

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("campaign-meeting-invitation"), contextCaptor.capture());
        String templateDescription = (String) contextCaptor.getValue().getVariable("meetingDescription");
        assertTrue(templateDescription.contains("<p>Hello mgmies!</p>"));
        assertTrue(templateDescription.contains("<strong>mgmGives</strong>"));
        assertFalse(templateDescription.contains("<script>"));
        assertFalse(templateDescription.contains("&lt;p&gt;"));

        MimeMultipart multipart = (MimeMultipart) mimeMessage.getContent();
        String plainText = (String) multipart.getBodyPart(0).getContent();
        assertTrue(plainText.contains("Hello mgmies!"));
        assertTrue(plainText.contains("Support mgmGives."));
        assertFalse(plainText.contains("<p>"));
        assertFalse(plainText.contains("<strong>"));
        assertFalse(plainText.contains("<script>"));
    }

    private CalendarMeetingEmailRequest request(String method) {
        return request(method, "Description");
    }

    private CalendarMeetingEmailRequest request(String method, String meetingDescription) {
        return new CalendarMeetingEmailRequest(
                method,
                "alice@example.com",
                "Alice",
                "Campaign",
                "Kickoff",
                meetingDescription,
                "Host",
                "host@example.com",
                "https://webex.example/join",
                null,
                10L,
                LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 11, 0),
                "uid",
                0,
                List.of(new CalendarAttendee("alice@example.com", "Alice"))
        );
    }
}
