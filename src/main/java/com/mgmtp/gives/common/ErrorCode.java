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

    CATEGORY_NOT_FOUND(1020, "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_NAME_ALREADY_EXISTS(1021, "Category Name Already Exists", HttpStatus.BAD_REQUEST),

    CATEGORY_NOT_AVAILABLE(1022, "Category is not available", HttpStatus.BAD_REQUEST),

    CAMPAIGN_NOT_FOUND(2001, "Campaign not found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED_CAMPAIGN_UPDATE(2003, "You do not have permission to update this campaign", HttpStatus.FORBIDDEN),
    INVALID_CAMPAIGN_STATUS_FOR_UPDATE(2004, "Cannot update campaign in current state", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
