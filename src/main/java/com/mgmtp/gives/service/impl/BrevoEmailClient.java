package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailClient {
    private static final String SEND_EMAIL_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String SENDER_NAME = "mgmGives";

    private final RestClient restClient;
    private final MailProps mailProps;

    public boolean isConfigured() {
        return mailProps.getBrevoApiKey() != null && !mailProps.getBrevoApiKey().isBlank();
    }

    public void sendHtml(String toEmail, String subject, String htmlContent) {
        sendHtml(toEmail, subject, htmlContent, null, null, null);
    }

    public void sendHtml(
            String toEmail,
            String subject,
            String htmlContent,
            byte[] attachmentBytes,
            String attachmentFilename,
            String attachmentContentType
    ) {
        List<Attachment> attachments = attachmentBytes == null
                ? null
                : List.of(new Attachment(
                        Base64.getEncoder().encodeToString(attachmentBytes),
                        attachmentFilename
                ));

        SendEmailRequest request = new SendEmailRequest(
                new Contact(mailProps.getFromMail(), SENDER_NAME),
                List.of(new Contact(toEmail, toEmail)),
                subject,
                htmlContent,
                attachments
        );

        try {
            restClient.post()
                    .uri(SEND_EMAIL_URL)
                    .header("api-key", mailProps.getBrevoApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Brevo API accepted email. subject={}, to={}", subject, toEmail);
        } catch (RestClientException exception) {
            log.error("Brevo API rejected email. subject={}, to={}", subject, toEmail, exception);
            throw new AppException(ErrorCode.EMAIL_SENT_FAILURE, exception.getMessage());
        }
    }

    private record SendEmailRequest(
            Contact sender,
            List<Contact> to,
            String subject,
            String htmlContent,
            List<Attachment> attachment
    ) {
    }

    private record Contact(String email, String name) {
    }

    private record Attachment(String content, String name) {
    }
}
