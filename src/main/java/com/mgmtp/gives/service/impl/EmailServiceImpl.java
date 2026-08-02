package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;
import com.mgmtp.gives.enums.TokenType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.ICalendarService;
import com.mgmtp.gives.util.HtmlSanitizerUtil;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final MailProps mailProps;
    private final JavaMailSender mailSender;
    private final BrevoEmailClient brevoEmailClient;
    private static final String SENDER_NAME = "mgmGives";
    private static final String TEMPLATE_VAR_FULL_NAME = "fullName";
    private static final String TEMPLATE_VAR_LINK = "link";
    private static final String TEMPLATE_CAMPAIGN_MEETING_INVITATION = "campaign-meeting-invitation";
    private static final String TEMPLATE_CAMPAIGN_MEETING_CANCELLATION = "campaign-meeting-cancellation";
    private static final String TEMPLATE_VAR_CAMPAIGN_NAME = "campaignName";
    private static final String TEMPLATE_VAR_MEETING_TITLE = "meetingTitle";
    private static final String TEMPLATE_VAR_MEETING_DESCRIPTION = "meetingDescription";
    private static final String TEMPLATE_VAR_CREATED_BY_NAME = "createdByName";
    private static final String TEMPLATE_VAR_MEETING_URL = "meetingUrl";
    private static final String TEMPLATE_VAR_LOCATION = "location";
    private static final String TEMPLATE_VAR_START_TIME = "startTime";
    private static final String TEMPLATE_VAR_END_TIME = "endTime";
    private static final String TEMPLATE_VAR_LINK_TO_CAMPAIGN = "linkToCampaign";
    private static final DateTimeFormatter MEETING_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final TemplateEngine templateEngine;
    private final ICalendarService iCalendarService;

    @Override
    @Async("notificationExecutor")
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        log.info("Send verification email requested. to={}", toEmail);
        sendEmail(toEmail, fullName, token, TokenType.VERIFY_EMAIL);
    }

    @Override
    @Async("notificationExecutor")
    public void sendResetPasswordEmail(String toEmail, String fullName, String token) {
        log.info("Send reset password email requested. to={}", toEmail);
        sendEmail(toEmail, fullName, token, TokenType.RESET_PASSWORD);
    }

    @Override
    public void sendCampaignMeetingInvitation(String toEmail, String fullName, String campaignName, String meetingTitle,
            String meetingDescription, String createdByName, String meetingUrl, String location, Long campaignId,
            String startTime, String endTime) {

        sendCampaignMeeting(toEmail, fullName, campaignName, meetingTitle, meetingDescription, createdByName, meetingUrl,
                location, campaignId, startTime, endTime, "mgmGives meeting invitation: " + meetingTitle,
                TEMPLATE_CAMPAIGN_MEETING_INVITATION
        );
    }

    @Override
    public void sendCampaignMeetingCancellation(String toEmail, String fullName, String campaignName, String meetingTitle,
            String meetingDescription, String createdByName, String location, String startTime, String endTime) {

        sendCampaignMeeting(toEmail, fullName, campaignName, meetingTitle, meetingDescription, createdByName, null,
                location, null, startTime, endTime, "mgmGives meeting cancelled: " + meetingTitle,
                TEMPLATE_CAMPAIGN_MEETING_CANCELLATION
        );
    }

    @Override
    public void sendCampaignMeetingCalendarEmail(CalendarMeetingEmailRequest request) {
        String method = calendarMethod(request);
        String subject = calendarSubject(request, method);
        String template = "CANCEL".equals(method)
                ? TEMPLATE_CAMPAIGN_MEETING_CANCELLATION
                : TEMPLATE_CAMPAIGN_MEETING_INVITATION;
        String link = buildCampaignLink(request.campaignId());
        String startTime = formatMeetingTime(request.startTime());
        String endTime = formatMeetingTime(request.endTime());
        Context context = meetingContext(
                request.fullName(),
                request.campaignName(),
                request.meetingTitle(),
                request.meetingDescription(),
                request.organizerName(),
                startTime,
                endTime,
                link
        );
        context.setVariable(TEMPLATE_VAR_MEETING_URL, request.meetingUrl());
        context.setVariable(TEMPLATE_VAR_LOCATION, request.location());

        String htmlContent = templateEngine.process(template, context);
        String plainText = plainCalendarText(request, method, startTime, endTime, link);
        String calendarContent = iCalendarService.generate(request);

        sendCalendarMimeEmail(request.toEmail(), subject, plainText, htmlContent, calendarContent, method);
    }

    @Async
    public void executeSend(String toEmail, String content, TokenType type) {
        if (brevoEmailClient.isConfigured()) {
            brevoEmailClient.sendHtml(toEmail, type.getSubject(), content);
            log.info("Email sent successfully through Brevo API. type={}, to={}", type, toEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, type.isMultipart(), StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(mailProps.getFromMail(), SENDER_NAME));
            helper.setTo(toEmail);
            helper.setSubject(type.getSubject());
            helper.setText(content, type.isHtml());

            mailSender.send(message);
            log.info("Email sent successfully. type={}, to={}", type, toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email. type={}, to={}", type, toEmail, e);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, e.getMessage());
        }
    }

    @Async
    @Override
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        if (brevoEmailClient.isConfigured()) {
            brevoEmailClient.sendHtml(toEmail, subject, htmlContent);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProps.getFromMail());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("HTML email sent: subject={}, to={}", subject, toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email: subject={}, to={}", subject, toEmail, e);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, e.getMessage());
        }
    }

    @Async
    @Override
    public void sendHtmlEmailWithAttachment(
            String toEmail,
            String subject,
            String htmlContent,
            byte[] attachmentBytes,
            String attachmentFilename,
            String attachmentContentType
    ) {
        if (brevoEmailClient.isConfigured()) {
            brevoEmailClient.sendHtml(
                    toEmail,
                    subject,
                    htmlContent,
                    attachmentBytes,
                    attachmentFilename,
                    attachmentContentType
            );
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProps.getFromMail());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes), attachmentContentType);
            mailSender.send(message);
            log.info("HTML email with attachment sent: subject={}, to={}, attachment={}",
                    subject, toEmail, attachmentFilename);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email with attachment: subject={}, to={}", subject, toEmail, e);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, e.getMessage());
        }
    }

    private void sendEmail(String toEmail, String fullName, String token, TokenType type) {
        String link = UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .path(type.getUri())
                .queryParam("token", token)
                .toUriString();
        log.debug("Built email link. type={}, link={}", type, link);

        log.info("==================================================");
        log.info("LOCAL DEVELOPMENT EMAIL LINK ({}):", type.name());
        log.info("To: {}", toEmail);
        log.info("Link: {}", link);
        log.info("==================================================");

        Context context = new Context();
        context.setVariable(TEMPLATE_VAR_FULL_NAME, fullName);
        context.setVariable(TEMPLATE_VAR_LINK, link);

        String content = templateEngine.process(type.getTemplate(), context);
        executeSend(toEmail, content, type);
    }

    private void sendTemplatedEmail(String toEmail, String subject, String template, Context context) {
        String content = templateEngine.process(template, context);
        sendRawHtmlEmail(toEmail, subject, content);
    }

    private void sendCampaignMeeting(String toEmail, String fullName, String campaignName, String meetingTitle,
            String meetingDescription, String createdByName, String meetingUrl, String location, Long campaignId,
            String startTime, String endTime, String subject, String template) {

        String link = buildCampaignLink(campaignId);

        Context context = meetingContext(fullName, campaignName, meetingTitle, meetingDescription,
                createdByName, startTime, endTime, link);

        context.setVariable(TEMPLATE_VAR_MEETING_URL, meetingUrl);
        context.setVariable(TEMPLATE_VAR_LOCATION, location);
        sendTemplatedEmail(toEmail, subject, template, context);
    }

    private String buildCampaignLink(Long campaignId) {
        if (campaignId == null) {
            return null;
        }
        return UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .pathSegment("campaigns", campaignId.toString())
                .toUriString();
    }

    private void sendRawHtmlEmail(String toEmail, String subject, String content) {
        if (brevoEmailClient.isConfigured()) {
            brevoEmailClient.sendHtml(toEmail, subject, content);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(mailProps.getFromMail(), SENDER_NAME));
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Campaign meeting email sent successfully. to={}, subject={}", toEmail, subject);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send campaign meeting email. to={}, subject={}", toEmail, subject, e);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, e.getMessage());
        }
    }

    private void sendCalendarMimeEmail(
            String toEmail,
            String subject,
            String plainText,
            String htmlContent,
            String calendarContent,
            String method
    ) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            message.setFrom(new InternetAddress(mailProps.getFromMail(), SENDER_NAME));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setHeader("Content-Class", "urn:content-classes:calendarmessage");
            message.setHeader("X-MS-OLK-FORCEINSPECTOROPEN", "TRUE");

            MimeMultipart alternative = new MimeMultipart("alternative");
            alternative.addBodyPart(textPart(plainText, "text/plain; charset=UTF-8"));
            alternative.addBodyPart(textPart(htmlContent, "text/html; charset=UTF-8"));

            MimeBodyPart calendarPart = new MimeBodyPart();
            calendarPart.setContent(calendarContent, calendarContentType(method));
            calendarPart.setHeader("Content-Class", "urn:content-classes:calendarmessage");
            calendarPart.setHeader("Content-Transfer-Encoding", "8bit");
            calendarPart.setDisposition(Part.INLINE);
            calendarPart.setFileName("CANCEL".equals(method) ? "cancel.ics" : "invite.ics");
            alternative.addBodyPart(calendarPart);

            message.setContent(alternative);
            message.saveChanges();
            mailSender.send(message);
            log.info("Campaign meeting calendar email sent successfully. to={}, subject={}, method={}",
                    toEmail, subject, method);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send campaign meeting calendar email. to={}, subject={}, method={}",
                    toEmail, subject, method, e);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, e.getMessage());
        }
    }

    private BodyPart textPart(String content, String contentType) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(content == null ? "" : content, contentType);
        part.setHeader("Content-Transfer-Encoding", "8bit");
        return part;
    }

    private String calendarContentType(String method) {
        return "text/calendar; method=" + method + "; component=VEVENT; charset=UTF-8";
    }

    private String calendarMethod(CalendarMeetingEmailRequest request) {
        return "CANCEL".equalsIgnoreCase(request.method()) ? "CANCEL" : "REQUEST";
    }

    private String calendarSubject(CalendarMeetingEmailRequest request, String method) {
        String prefix = "CANCEL".equals(method) ? "mgmGives meeting cancelled: " : "mgmGives meeting invitation: ";
        return prefix + request.meetingTitle();
    }

    private String plainCalendarText(
            CalendarMeetingEmailRequest request,
            String method,
            String startTime,
            String endTime,
            String campaignLink
    ) {
        StringBuilder builder = new StringBuilder();
        if ("CANCEL".equals(method)) {
            builder.append("This campaign meeting was cancelled.").append('\n');
        } else {
            builder.append("You are invited to a campaign meeting.").append('\n');
        }
        builder.append("Campaign: ").append(nullToEmpty(request.campaignName())).append('\n');
        builder.append("Meeting: ").append(nullToEmpty(request.meetingTitle())).append('\n');
        builder.append("Organizer: ").append(nullToEmpty(request.organizerName())).append('\n');
        builder.append("Time: ").append(startTime).append(" - ").append(endTime).append('\n');
        if (StringUtils.hasText(request.location())) {
            builder.append("Location: ").append(request.location()).append('\n');
        }
        if (StringUtils.hasText(request.meetingUrl())) {
            builder.append("Webex: ").append(request.meetingUrl()).append('\n');
        }
        String plainDescription = htmlToPlainText(request.meetingDescription());
        if (StringUtils.hasText(plainDescription)) {
            builder.append('\n').append("Message:").append('\n').append(plainDescription).append('\n');
        }
        if (StringUtils.hasText(campaignLink)) {
            builder.append("Campaign page: ").append(campaignLink).append('\n');
        }
        return builder.toString();
    }

    private String formatMeetingTime(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(MEETING_DISPLAY_FORMATTER);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String displayName(String fullName) {
        return fullName == null || fullName.isBlank() ? "there" : fullName;
    }

    private Context meetingContext(
            String fullName,
            String campaignName,
            String meetingTitle,
            String meetingDescription,
            String createdByName,
            String startTime,
            String endTime,
            String linkToCampaign
    ) {
        Context context = new Context();
        context.setVariable(TEMPLATE_VAR_FULL_NAME, displayName(fullName));
        context.setVariable(TEMPLATE_VAR_CAMPAIGN_NAME, campaignName);
        context.setVariable(TEMPLATE_VAR_MEETING_TITLE, meetingTitle);
        context.setVariable(TEMPLATE_VAR_MEETING_DESCRIPTION, sanitizeHtml(meetingDescription));
        context.setVariable(TEMPLATE_VAR_CREATED_BY_NAME, displayName(createdByName));
        context.setVariable(TEMPLATE_VAR_START_TIME, startTime);
        context.setVariable(TEMPLATE_VAR_END_TIME, endTime);
        context.setVariable(TEMPLATE_VAR_LINK_TO_CAMPAIGN, linkToCampaign);
        return context;
    }

    private String sanitizeHtml(String html) {
        return StringUtils.hasText(html) ? HtmlSanitizerUtil.sanitize(decodeHtmlEntities(html)) : null;
    }

    private String htmlToPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return null;
        }

        String htmlWithLineBreaks = decodeHtmlEntities(html)
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

    private String decodeHtmlEntities(String value) {
        return Parser.unescapeEntities(value, false);
    }

    @Override
    public void sendTaskAssignmentEmail(
            String toEmail,
            String assigneeName,
            String campaignName,
            String taskTitle,
            String taskDescriptionSnippet,
            String dueDate,
            String taskUrl
    ) {
        log.info("Send task assignment email requested. to={}", toEmail);

        Context context = new Context();
        context.setVariable("assigneeName", assigneeName);
        context.setVariable("campaignName", campaignName);
        context.setVariable("taskTitle", taskTitle);
        context.setVariable("taskDescriptionSnippet", taskDescriptionSnippet);
        context.setVariable("dueDate", dueDate);
        context.setVariable("taskUrl", taskUrl);

        String subject = "You Have Been Assigned a New Task: " + taskTitle;
        sendTemplatedEmail(toEmail, subject, "task-assignment", context);
    }
}
