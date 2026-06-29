package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationPayload;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Notification;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.notification.NotificationPublisher;
import com.mgmtp.gives.repository.NotificationRepository;
import com.mgmtp.gives.service.NotificationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final EntityManager entityManager;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notificationPublisher;


    @Override @Transactional
    public void createNotification(CreateNotificationCommand command) {
        Set<NotificationRecipient> recipients = sanitizeRecipients(command.recipients());

        log.info(
                "Creating notifications: type={}, recipientCount={}, linkUrl={}",
                command.type(),
                recipients.size(),
                command.linkUrl()
        );

        if (recipients.isEmpty()) {
            log.debug("Notification skipped because recipient list is empty: type={}", command.type());
            return;
        }

        List<Notification> notifications = recipients.stream()
                .map(recipient -> Notification.builder()
                        .user(entityManager.getReference(User.class, recipient.userId()))
                        .title(command.title())
                        .message(command.message())
                        .type(command.type())
                        .isRead(false)
                        .linkUrl(command.linkUrl())
                        .build())
                .toList();

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);
        List<NotificationRecipient> recipientList = recipients.stream().toList();

        for (int index = 0; index < savedNotifications.size(); index++) {
            Notification savedNotification = savedNotifications.get(index);
            NotificationRecipient recipient = recipientList.get(index);

            log.info(
                    "Publishing realtime notification: notificationId={}, type={}, recipientUserId={}, recipientEmail={}",
                    savedNotification.getId(),
                    savedNotification.getType(),
                    recipient.userId(),
                    recipient.email()
            );

            notificationPublisher.publishToUser(
                    recipient.email(),
                    new NotificationPayload(
                            savedNotification.getId(),
                            savedNotification.getType(),
                            savedNotification.getTitle(),
                            savedNotification.getMessage(),
                            savedNotification.getLinkUrl()
                    )
            );
        }

        log.info(
                "Notifications created: type={}, recipients={}, created={}",
                command.type(),
                recipients.size(),
                savedNotifications.size()
        );
    }

    private Set<NotificationRecipient> sanitizeRecipients(Set<NotificationRecipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return Set.of();
        }

        Set<NotificationRecipient> sanitizedRecipients = new LinkedHashSet<>();

        recipients.stream()
                .filter(Objects::nonNull)
                .filter(recipient -> recipient.userId() != null)
                .filter(recipient -> recipient.email() != null && !recipient.email().isBlank())
                .forEach(sanitizedRecipients::add);

        return sanitizedRecipients;
    }
}
