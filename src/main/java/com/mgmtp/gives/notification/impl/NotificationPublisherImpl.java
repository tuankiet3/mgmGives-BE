package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.NotificationPayload;
import com.mgmtp.gives.notification.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationPublisherImpl implements NotificationPublisher {
    private static final String DEFAULT_DESTINATION = "/queue/notifications";
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishToUser(String userEmail, NotificationPayload payload) {
        log.info(
                "Sending realtime notification: userEmail={}, destination={}, notificationId={}, type={}",
                userEmail,
                DEFAULT_DESTINATION,
                payload.notificationId(),
                payload.type()
        );

        messagingTemplate.convertAndSendToUser(
                userEmail,
                DEFAULT_DESTINATION,
                payload
        );

        log.debug(
                "Realtime notification sent: userEmail={}, notificationId={}, type={}",
                userEmail,
                payload.notificationId(),
                payload.type()
        );
    }
}
