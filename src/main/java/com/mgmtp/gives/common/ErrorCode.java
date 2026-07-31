package com.mgmtp.gives.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_ERROR(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_ENUM_VALUE(9998, "Invalid filter value provided", HttpStatus.BAD_REQUEST),

    USER_NOT_FOUND(1001, "User not found", HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND(1005, "Role not found", HttpStatus.NOT_FOUND),

    INVALID_TOKEN(1002, "Invalid Token", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN(1003, "Expired Token", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(1004, "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1006, "Unauthorized", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(1009, "Invalid email or password", HttpStatus.UNAUTHORIZED),

    EMAIL_ALREADY_EXISTS(1010, "Email is already exists", HttpStatus.CONFLICT),
    EMAIL_SENT_FAILURE(1011, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(1012, "Validation failed", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED(1013, "Account is locked", HttpStatus.FORBIDDEN),
    ACCOUNT_INACTIVE(1014, "Account is inactive", HttpStatus.FORBIDDEN),
    PASSWORDS_DO_NOT_MATCH(1015, "Passwords do not match", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_VERIFIED(1016, "Email is already verified", HttpStatus.BAD_REQUEST),
    PASSWORD_INCORRECT(1017, "Current password is incorrect", HttpStatus.BAD_REQUEST),

    CATEGORY_NOT_FOUND(1020, "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_NAME_ALREADY_EXISTS(1021, "Category Name Already Exists", HttpStatus.BAD_REQUEST),
    CATEGORY_NOT_AVAILABLE(1022, "Category is not available", HttpStatus.BAD_REQUEST),
    CATEGORY_ALREADY_EXISTS_BUT_DELETED(1023, "Category already exists in archives", HttpStatus.BAD_REQUEST),

    CAMPAIGN_NOT_IN_PROGRESS(2000, "Campaign not in progress", HttpStatus.BAD_REQUEST),
    CAMPAIGN_NOT_FOUND(2001, "Campaign not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_CAMPAIGN_UPDATE(2003, "You do not have permission to update this campaign", HttpStatus.FORBIDDEN),
    INVALID_CAMPAIGN_STATUS_FOR_UPDATE(2004, "Cannot update campaign in current state", HttpStatus.BAD_REQUEST),
    CAMPAIGN_ALREADY_JOINED(2005, "User has already joined this campaign", HttpStatus.CONFLICT),
    UNAUTHORIZED_CAMPAIGN_ACCESS(2007, "You do not have permission to access this campaign", HttpStatus.FORBIDDEN),
    UNAUTHORIZED_CAMPAIGN_DELETE(2008, "You do not have permission to delete this campaign", HttpStatus.FORBIDDEN),
    INVALID_CAMPAIGN_STATUS_FOR_DELETE(2009, "Cannot delete campaign in current state", HttpStatus.BAD_REQUEST),
    REJECTION_REASON_REQUIRED(2006, "Rejection reason is required", HttpStatus.BAD_REQUEST),
    CAMPAIGN_NOT_COMPLETED(2010, "Campaign is not completed yet", HttpStatus.BAD_REQUEST),
    CAMPAIGN_RESULT_NOT_FOUND(2011, "Campaign result not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_RESULT_ACCESS(2012, "Only Campaign Admin or Admin can post or edit the result", HttpStatus.FORBIDDEN),
    CAMPAIGN_RESULT_ALREADY_POSTED(2013, "Campaign result has already been posted", HttpStatus.CONFLICT),
    GEMINI_API_ERROR(2014, "Failed to generate result draft with AI", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CAMPAIGN_STATUS_FOR_REVIEW(2015, "Campaign is not in a reviewable state", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ANNOUNCEMENT_ACCESS(2016, "You do not have permission to manage announcements for this campaign",
            HttpStatus.FORBIDDEN),
    CAMPAIGN_ADMIN_CANNOT_LEAVE(2018, "Campaign admins cannot leave the campaign", HttpStatus.FORBIDDEN),
    ANNOUNCEMENT_NOT_FOUND(2017, "Announcement not found", HttpStatus.NOT_FOUND),
    REPLY_NOT_FOUND(2019, "Reply not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_REPLY_ACTION(2020, "You do not have permission to perform this action on this reply",
            HttpStatus.FORBIDDEN),
    CAMPAIGN_UNJOIN_REQUEST_NOT_FOUND(2021, "No pending unjoin request found for this member", HttpStatus.NOT_FOUND),
    CAMPAIGN_MEMBER_NOT_FOUND(2022, "You are not a member of this campaign", HttpStatus.NOT_FOUND),

    CAMPAIGN_MEDIA_NOT_FOUND(3001, "Campaign media not found", HttpStatus.NOT_FOUND),
    MEDIA_NOT_FOUND(3002, "Media file not found", HttpStatus.NOT_FOUND),
    MEDIA_ALREADY_DELETED(3003, "Media has already been deleted", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_DELETED(3004, "Media is not deleted, nothing to restore", HttpStatus.BAD_REQUEST),
    MEDIA_RESTORE_EXPIRED(3005, "Restore window has expired (14 days)", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(3006, "Unsupported file type", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED(3007,
            "File size exceeds the allowed limit. Max size: Image (15MB), Video (200MB), Document (10MB)",
            HttpStatus.BAD_REQUEST),
    IMAGE_ONLY(3008, "Only image files are allowed for avatar/cover", HttpStatus.BAD_REQUEST),
    PATH_TRAVERSAL_DETECTED(3009, "Invalid file path", HttpStatus.BAD_REQUEST),
    WEBEX_NOT_CONNECTED(4001, "Please connect your Webex account before creating a meeting", HttpStatus.BAD_REQUEST),
    WEBEX_AUTHORIZATION_FAILED(4002, "Webex authorization failed", HttpStatus.UNAUTHORIZED),
    WEBEX_CONNECTION_NOT_FOUND(4003, "Webex connection not found", HttpStatus.NOT_FOUND),
    MEETING_TIME_CONFLICT(4004, "This campaign already has a meeting scheduled during this time", HttpStatus.CONFLICT),
    DONATE_NOT_FOUND(3010, "Donate not found", HttpStatus.NOT_FOUND),
    NOTIFICATION_NOT_FOUND(3011, "Notification not found", HttpStatus.NOT_FOUND),
    DONATION_NOT_PENDING(3012, "Only pending donations can have proof submitted", HttpStatus.BAD_REQUEST),

    TASK_NOT_FOUND(5001, "Task not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_TASK_ACCESS(5002, "You do not have permission to manage tasks for this campaign",
            HttpStatus.FORBIDDEN),
    UNAUTHORIZED_TASK_STATUS_UPDATE(5003, "You do not have permission to update this task's status",
            HttpStatus.FORBIDDEN),
    INVALID_TASK_ASSIGNEES(5004, "One or more assignees are not members of this campaign", HttpStatus.BAD_REQUEST),
    INVALID_TASK_LABELS(5005, "One or more labels do not belong to this campaign", HttpStatus.BAD_REQUEST),
    SELF_ASSIGN_NOT_ALLOWED(5006, "Campaign admin cannot self-assign tasks", HttpStatus.BAD_REQUEST),
    LABEL_NOT_FOUND(5007, "Label not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_LABEL_ACCESS(5008, "You do not have permission to manage labels for this campaign",
            HttpStatus.FORBIDDEN),
    TASK_DUE_DATE_IN_PAST(5009, "Task due date must be in the future", HttpStatus.BAD_REQUEST),
    TASK_ALREADY_ASSIGNED(5010, "User is already assigned to this task", HttpStatus.BAD_REQUEST),
    TASK_DUE_DATE_BEFORE_CAMPAIGN_START(5011, "Task due date cannot be before campaign start date",
            HttpStatus.BAD_REQUEST),
    TASK_DUE_DATE_AFTER_CAMPAIGN_END(5012, "Task due date cannot be after campaign end date", HttpStatus.BAD_REQUEST),
    ASSIGNEE_NOT_FOUND(5013, "Assignee not found in this task", HttpStatus.NOT_FOUND),
    RESOURCE_UPDATE_CONFLICT(5014, "This resource has been updated by another user. Please refresh and try again",
            HttpStatus.CONFLICT),

    SPENDING_NOT_FOUND(6001, "Spending entry not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_SPENDING_ACCESS(6002, "You do not have permission to manage spending for this campaign",
            HttpStatus.FORBIDDEN),
    SPENDING_DATE_BEFORE_CAMPAIGN_START(6003, "Spending date cannot be before campaign start date",
            HttpStatus.BAD_REQUEST),
    CAMPAIGN_NOT_IN_PROGRESS_FOR_SPENDING(6004, "Spending can only be logged while the campaign is in progress",
            HttpStatus.BAD_REQUEST),
    SPENDING_DELETE_NOT_ALLOWED(6005, "Spending can only be deleted while the campaign is in progress",
            HttpStatus.BAD_REQUEST),
    SPENDING_DATE_IN_FUTURE(6006, "Spending date cannot be in the future", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
