package com.mgmtp.gives.enums;

import lombok.Getter;

@Getter
public enum TokenType {
    VERIFY_EMAIL("verify-email", "/verify-email", "Verify Your Email", false, true),
    RESET_PASSWORD("reset-password", "/reset-password", "Reset Your Password", false, true);

    private final String template;
    private final String uri;
    private final String subject;
    private final boolean multipart;
    private final boolean html;

    TokenType(String template, String uri, String subject, boolean multipart, boolean html) {
        this.template = template;
        this.uri = uri;
        this.subject = subject;
        this.multipart = multipart;
        this.html = html;
    }
}
