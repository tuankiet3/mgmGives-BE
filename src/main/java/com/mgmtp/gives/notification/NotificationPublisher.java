package com.mgmtp.gives.notification;

import com.mgmtp.gives.dto.notification.NotificationPayload;

public interface NotificationPublisher {
    void publishToUser(String userEmail, NotificationPayload payload);
}
