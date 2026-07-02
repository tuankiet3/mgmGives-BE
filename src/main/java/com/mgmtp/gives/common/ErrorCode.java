package com.mgmtp.gives.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_ERROR(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),

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

    CAMPAIGN_MEDIA_NOT_FOUND(3001, "Campaign media not found", HttpStatus.NOT_FOUND),
    MEDIA_NOT_FOUND(3002, "Media file not found", HttpStatus.NOT_FOUND),
    MEDIA_ALREADY_DELETED(3003, "Media has already been deleted", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_DELETED(3004, "Media is not deleted, nothing to restore", HttpStatus.BAD_REQUEST),
    MEDIA_RESTORE_EXPIRED(3005, "Restore window has expired (14 days)", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(3006, "Unsupported file type", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED(3007, "File size exceeds the allowed limit", HttpStatus.BAD_REQUEST),
    IMAGE_ONLY(3008, "Only image files are allowed for avatar/cover", HttpStatus.BAD_REQUEST),
    PATH_TRAVERSAL_DETECTED(3009, "Invalid file path", HttpStatus.BAD_REQUEST),
    WEBEX_NOT_CONNECTED(4001, "Please connect your Webex account before creating a meeting", HttpStatus.BAD_REQUEST),
    WEBEX_AUTHORIZATION_FAILED(4002, "Webex authorization failed", HttpStatus.UNAUTHORIZED),
    WEBEX_CONNECTION_NOT_FOUND(4003, "Webex connection not found", HttpStatus.NOT_FOUND),
    MEETING_TIME_CONFLICT(4004, "This campaign already has a meeting scheduled during this time", HttpStatus.CONFLICT),
    DONATE_NOT_FOUND(3010, "Donate not found", HttpStatus.NOT_FOUND),
    NOTIFICATION_NOT_FOUND(3011, "Notification not found", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
