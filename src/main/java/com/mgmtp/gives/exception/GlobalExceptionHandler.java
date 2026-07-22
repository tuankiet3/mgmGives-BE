package com.mgmtp.gives.exception;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.ErrorCode;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<?>> handleOptimisticLocking(Exception ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.fail(
                ErrorCode.RESOURCE_UPDATE_CONFLICT,
                ErrorCode.RESOURCE_UPDATE_CONFLICT.getMessage(),
                request.getRequestURI());

        return ResponseEntity.status(ErrorCode.RESOURCE_UPDATE_CONFLICT.getStatus()).body(response);
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

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path;
            errors.put(field, violation.getMessage());
        });
        ApiResponse<?> response = ApiResponse.fail(errors, ErrorCode.VALIDATION_ERROR, request.getRequestURI());

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus()).body(response);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("MaxUploadSizeExceededException Message: {}", ex.getMessage());
        ApiResponse<?> response = ApiResponse.fail(null, ErrorCode.FILE_SIZE_EXCEEDED, "File size exceeds the allowed limit (max 15MB for images, 200MB for videos, 10MB for documents)", request.getRequestURI());
        return ResponseEntity.status(ErrorCode.FILE_SIZE_EXCEEDED.getStatus()).body(response);
    }
}
