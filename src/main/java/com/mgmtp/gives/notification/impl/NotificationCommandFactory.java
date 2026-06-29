package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.notification.NotificationRecipientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class NotificationCommandFactory {
    private final NotificationRecipientResolver recipientResolver;

}
