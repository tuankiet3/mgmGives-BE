package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.notification.NotificationResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification APIs", description = "Endpoints for managing notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Get paginated notifications for the current user")
    public ApiResponse<Page<NotificationResponse>> getUserNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationResponse> notifications = notificationService.getUserNotifications(userDetails.getUser(), pageable);
        return ApiResponse.success(notifications);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get user unread notifications count", description = "Get count of unread notifications for the current user")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = notificationService.getUnreadCount(userDetails.getUser());
        return ApiResponse.success(count);
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAsRead(id, userDetails.getUser());
        return ApiResponse.success(null);
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ApiResponse<Void> markAllAsRead(@AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllAsRead(userDetails.getUser());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.deleteNotification(id, userDetails.getUser());
        return ApiResponse.success(null);
    }
}
