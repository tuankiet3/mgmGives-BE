package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.enums.TokenType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.service.EmailService;
import jakarta.mail.MessagingException;
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

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final MailProps mailProps;
    private final JavaMailSender mailSender;
    private static final String TEMPLATE_VAR_FULL_NAME = "fullName";
    private static final String TEMPLATE_VAR_LINK = "link";
    private final TemplateEngine templateEngine;

    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        log.info("Send verification email requested. to={}", toEmail);
        sendEmail(toEmail, fullName, token, TokenType.VERIFY_EMAIL);
    }

    @Override
    public void sendResetPasswordEmail(String toEmail, String fullName, String token) {
        sendEmail(toEmail, fullName, token, TokenType.RESET_PASSWORD);
    }

    public void executeSend(String toEmail, String content, TokenType type) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, type.isMultipart(), StandardCharsets.UTF_8.name());

            helper.setFrom(mailProps.getFromMail());
            helper.setTo(toEmail);
            helper.setSubject(type.getSubject());
            helper.setText(content, type.isHtml());

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send email. type={}, to={}", type, toEmail, e);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, e.getMessage());
        }
    }

    private void sendEmail(String toEmail, String fullName, String token, TokenType type) {
        String link = UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .path(type.getUri())
                .queryParam("token", token)
                .toUriString();

        Context context = new Context();
        context.setVariable(TEMPLATE_VAR_FULL_NAME, fullName);
        context.setVariable(TEMPLATE_VAR_LINK, link);

        String content = templateEngine.process(type.getTemplate(), context);
        executeSend(toEmail, content, type);
    }
}
