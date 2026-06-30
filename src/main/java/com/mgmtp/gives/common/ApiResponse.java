package com.mgmtp.gives.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        Integer code,
        String message,
        T result,
        Instant timestamp,
        String path) {

    public static <T> ApiResponse<T> success(T result) {
        return ApiResponse.<T>builder()
                .success(true)
                .result(result)
                .build();
    }

    public static <T> ApiResponse<T> success(T result, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .result(result)
                .build();
    }

    public static <T> ApiResponse<T> fail(T errors, ErrorCode errorCode, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .result(errors)
                .message(errorCode.getMessage())
                .code(errorCode.getCode())
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> fail(T errors, ErrorCode errorCode, String message, String path) {
        return ApiResponse.<T>builder()
                .success(false)
                .result(errors)
                .message(message)
                .code(errorCode.getCode())
                .timestamp(Instant.now())
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String uriPath) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(errorCode.getMessage())
                .code(errorCode.getCode())
                .timestamp(Instant.now())
                .path(uriPath)
                .build();
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, String uriPath) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .code(errorCode.getCode())
                .timestamp(Instant.now())
                .path(uriPath)
                .build();
    }
}
