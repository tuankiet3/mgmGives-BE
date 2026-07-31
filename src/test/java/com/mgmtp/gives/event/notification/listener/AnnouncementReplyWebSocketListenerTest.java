package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.dto.announcement.AnnouncementReplyResponse;
import com.mgmtp.gives.dto.announcement.ReplyWebSocketEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyCreatedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyDeletedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnnouncementReplyWebSocketListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AnnouncementReplyWebSocketListener listener;

    @Test
    void handleCreated_broadcastsWebSocketSignal() {
        AnnouncementReplyCreatedEvent event = new AnnouncementReplyCreatedEvent(
                1L, 2L, "Title", 3L, 4L, "Linh", 5L, 6L
        );

        listener.handleCreated(event);

        ArgumentCaptor<ReplyWebSocketEvent> captor = ArgumentCaptor.forClass(ReplyWebSocketEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/announcements/2/replies"), captor.capture());

        ReplyWebSocketEvent signal = captor.getValue();
        assertEquals("CREATED", signal.action());
        assertEquals(3L, signal.replyId());
        assertEquals(2L, signal.announcementId());
        assertNull(signal.reply());
    }

    @Test
    void handleUpdated_broadcastsWebSocketSignal() {
        AnnouncementReplyResponse replyResponse = new AnnouncementReplyResponse(
                3L, 2L, "Updated Content", null, true, LocalDateTime.now(), LocalDateTime.now(), null
        );
        AnnouncementReplyUpdatedEvent event = new AnnouncementReplyUpdatedEvent(
                1L, 2L, 3L, replyResponse
        );

        listener.handleUpdated(event);

        ArgumentCaptor<ReplyWebSocketEvent> captor = ArgumentCaptor.forClass(ReplyWebSocketEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/announcements/2/replies"), captor.capture());

        ReplyWebSocketEvent signal = captor.getValue();
        assertEquals("EDITED", signal.action());
        assertEquals(3L, signal.replyId());
        assertEquals(2L, signal.announcementId());
        assertEquals(replyResponse, signal.reply());
    }

    @Test
    void handleDeleted_broadcastsWebSocketSignal() {
        AnnouncementReplyDeletedEvent event = new AnnouncementReplyDeletedEvent(
                1L, 2L, 3L
        );

        listener.handleDeleted(event);

        ArgumentCaptor<ReplyWebSocketEvent> captor = ArgumentCaptor.forClass(ReplyWebSocketEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/announcements/2/replies"), captor.capture());

        ReplyWebSocketEvent signal = captor.getValue();
        assertEquals("DELETED", signal.action());
        assertEquals(3L, signal.replyId());
        assertEquals(2L, signal.announcementId());
        assertNull(signal.reply());
    }
}
