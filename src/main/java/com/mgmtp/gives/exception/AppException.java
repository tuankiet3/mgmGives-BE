package com.mgmtp.gives.exception;

import com.mgmtp.gives.common.ErrorCode;
import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object result;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.result = null;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.result = null;
    }

    public AppException(ErrorCode errorCode, Object result) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.result = result;
    }

    public AppException(ErrorCode errorCode, String message, Object result) {
        super(message);
        this.errorCode = errorCode;
        this.result = result;
    }
}
