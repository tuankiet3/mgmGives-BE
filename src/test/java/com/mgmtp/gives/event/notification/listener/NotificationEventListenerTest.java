package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.event.notification.AnnouncementReplyCreatedEvent;
import com.mgmtp.gives.notification.NotificationCommandFactory;
import com.mgmtp.gives.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationCommandFactory notificationCommandFactory;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    void handleAnnouncementReplyCreated_CreatesOneNotificationPerNonEmptyCommand() {
        AnnouncementReplyCreatedEvent event = event();
        CreateNotificationCommand directReply = command(2L);
        CreateNotificationCommand announcementReply = command(3L);
        when(notificationCommandFactory.announcementReplyCreated(event))
                .thenReturn(List.of(directReply, announcementReply));

        notificationEventListener.handleAnnouncementReplyCreated(event);

        verify(notificationService).createNotification(directReply);
        verify(notificationService).createNotification(announcementReply);
    }

    @Test
    void handleAnnouncementReplyCreated_SkipsEmptyRecipientCommands() {
        AnnouncementReplyCreatedEvent event = event();
        CreateNotificationCommand emptyCommand = CreateNotificationCommand.builder()
                .recipients(Set.of())
                .type(NotificationType.ANNOUNCEMENT_REPLY)
                .title("New reply")
                .message("Reply")
                .build();
        when(notificationCommandFactory.announcementReplyCreated(event)).thenReturn(List.of(emptyCommand));

        notificationEventListener.handleAnnouncementReplyCreated(event);

        verifyNoInteractions(notificationService);
    }

    private static AnnouncementReplyCreatedEvent event() {
        return new AnnouncementReplyCreatedEvent(1L, 2L, "Update", 3L, 4L, "Linh", 5L, 6L);
    }

    private static CreateNotificationCommand command(Long recipientId) {
        return CreateNotificationCommand.builder()
                .recipients(Set.of(new NotificationRecipient(recipientId, "user@example.com")))
                .type(NotificationType.ANNOUNCEMENT_REPLY)
                .title("New reply")
                .message("Reply")
                .build();
    }
}
