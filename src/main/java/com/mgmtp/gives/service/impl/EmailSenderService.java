package com.mgmtp.gives.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendActivationEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("MGM Gives – Confirm your email address");
        message.setText("Please click the link below to confirm your email:\n\n"
                + baseUrl + "/api/auth/confirm?token=" + token
                + "\n\nThis link expires in 1 hour.");
        mailSender.send(message);
    }
}
