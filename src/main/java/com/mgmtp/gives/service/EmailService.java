package com.mgmtp.gives.service;

import com.mgmtp.gives.enums.TokenType;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String fullName, String link);
    void executeSend(String toEmail, String content, TokenType type);
}
