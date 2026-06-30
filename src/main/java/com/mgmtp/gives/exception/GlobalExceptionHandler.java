package com.mgmtp.gives.exception;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice @Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("AppException Message: {}", ex.getMessage());
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getErrorCode().getMessage();
        ApiResponse<?> response = ApiResponse.fail(ex.getResult(), ex.getErrorCode(), message, request.getRequestURI());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        ApiResponse<?> response = ApiResponse.fail(errors, ErrorCode.VALIDATION_ERROR, request.getRequestURI());

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(response);
    }
}
