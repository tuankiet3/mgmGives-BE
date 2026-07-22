package com.mgmtp.gives.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standardized error response body returned by the GlobalExceptionHandler.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String error;
    private String message;

    /**
     * Field-level validation errors (populated only for MethodArgumentNotValidException).
     */
    private List<FieldError> fieldErrors;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Getter
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}
