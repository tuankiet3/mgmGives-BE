package com.mgmtp.gives.service;

import com.mgmtp.gives.enums.TokenType;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String fullName, String link);
    void sendResetPasswordEmail(String toEmail, String fullName, String token);
    void executeSend(String toEmail, String content, TokenType type);
}
