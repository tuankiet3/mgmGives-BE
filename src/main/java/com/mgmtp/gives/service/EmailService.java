package com.mgmtp.gives.service;

import com.mgmtp.gives.enums.TokenType;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String fullName, String link);

    void sendResetPasswordEmail(String toEmail, String fullName, String token);

    void sendCampaignMeetingInvitation(
            String toEmail,
            String fullName,
            String campaignName,
            String meetingTitle,
            String meetingDescription,
            String createdByName,
            String meetingUrl,
            Long campaignId,
            String startTime,
            String endTime
    );

    void sendCampaignMeetingCancellation(
            String toEmail,
            String fullName,
            String campaignName,
            String meetingTitle,
            String meetingDescription,
            String createdByName,
            String startTime,
            String endTime
    );

    void executeSend(String toEmail, String content, TokenType type);

    void sendHtmlEmail(String toEmail, String subject, String htmlContent);
}
