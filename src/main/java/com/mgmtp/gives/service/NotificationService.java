package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationResponse;
import com.mgmtp.gives.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    void createNotification(CreateNotificationCommand command);

    Page<NotificationResponse> getUserNotifications(User user, Pageable pageable);

    void markAsRead(Long id, User user);

    void markAllAsRead(User user);

    void deleteNotification(Long id, User user);

    void broadcastDonationUpdate(com.mgmtp.gives.entity.Donation donation);

    void broadcastDashboardUpdate();

    long getUnreadCount(User user);
}
