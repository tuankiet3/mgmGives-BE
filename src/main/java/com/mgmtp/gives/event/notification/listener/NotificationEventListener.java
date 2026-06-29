package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.notification.impl.NotificationCommandFactory;
import com.mgmtp.gives.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationEventListener {
    private final NotificationService notificationService;
    private final NotificationCommandFactory notificationCommandFactory;
}
