package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.enums.TokenType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final MailProps mailProps;
    private final JavaMailSender mailSender;
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
    private static final String TEMPLATE_VAR_START_TIME = "startTime";
    private static final String TEMPLATE_VAR_END_TIME = "endTime";
    private final TemplateEngine templateEngine;

    @Override
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        log.info("Send verification email requested. to={}", toEmail);
        sendEmail(toEmail, fullName, token, TokenType.VERIFY_EMAIL);
    }

    @Override
    public void sendResetPasswordEmail(String toEmail, String fullName, String token) {
        log.info("Send reset password email requested. to={}", toEmail);
        sendEmail(toEmail, fullName, token, TokenType.RESET_PASSWORD);
    }

    @Override
    public void sendCampaignMeetingInvitation(String toEmail, String fullName, String campaignName, String meetingTitle,
            String meetingDescription, String createdByName, String meetingUrl, String startTime, String endTime) {

        sendCampaignMeeting(toEmail, fullName, campaignName, meetingTitle, meetingDescription, createdByName, meetingUrl,
                startTime, endTime, "mgmGives meeting invitation: " + meetingTitle, TEMPLATE_CAMPAIGN_MEETING_INVITATION
        );
    }

    @Override
    public void sendCampaignMeetingCancellation(String toEmail, String fullName, String campaignName, String meetingTitle,
            String meetingDescription, String createdByName, String startTime, String endTime) {

        sendCampaignMeeting(toEmail, fullName, campaignName, meetingTitle, meetingDescription, createdByName, null,
                startTime, endTime, "mgmGives meeting cancelled: " + meetingTitle, TEMPLATE_CAMPAIGN_MEETING_CANCELLATION
        );
    }

    @Async
    public void executeSend(String toEmail, String content, TokenType type) {
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
            String meetingDescription, String createdByName, String meetingUrl, String startTime, String endTime,
            String subject, String template) {

        Context context = meetingContext(fullName, campaignName, meetingTitle, meetingDescription,
                createdByName, startTime, endTime);

        context.setVariable(TEMPLATE_VAR_MEETING_URL, meetingUrl);
        sendTemplatedEmail(toEmail, subject, template, context);
    }

    private void sendRawHtmlEmail(String toEmail, String subject, String content) {
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
            String endTime
    ) {
        Context context = new Context();
        context.setVariable(TEMPLATE_VAR_FULL_NAME, displayName(fullName));
        context.setVariable(TEMPLATE_VAR_CAMPAIGN_NAME, campaignName);
        context.setVariable(TEMPLATE_VAR_MEETING_TITLE, meetingTitle);
        context.setVariable(TEMPLATE_VAR_MEETING_DESCRIPTION, meetingDescription);
        context.setVariable(TEMPLATE_VAR_CREATED_BY_NAME, displayName(createdByName));
        context.setVariable(TEMPLATE_VAR_START_TIME, startTime);
        context.setVariable(TEMPLATE_VAR_END_TIME, endTime);
        return context;
    }
}
