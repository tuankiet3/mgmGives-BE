package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;
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
            String location,
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
            String location,
            String startTime,
            String endTime
    );

    void sendCampaignMeetingCalendarEmail(CalendarMeetingEmailRequest request);

    void executeSend(String toEmail, String content, TokenType type);

    void sendHtmlEmail(String toEmail, String subject, String htmlContent);

    void sendHtmlEmailWithAttachment(
            String toEmail,
            String subject,
            String htmlContent,
            byte[] attachmentBytes,
            String attachmentFilename,
            String attachmentContentType
    );

    void sendTaskAssignmentEmail(
            String toEmail,
            String assigneeName,
            String campaignName,
            String taskTitle,
            String taskDescriptionSnippet,
            String dueDate,
            String taskUrl
    );
}
