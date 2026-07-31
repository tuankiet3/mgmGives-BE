package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.dto.announcement.ReplyWebSocketEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyCreatedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyDeletedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementReplyWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreated(AnnouncementReplyCreatedEvent event) {
        log.info("Broadcasting reply CREATED event to WS: announcementId={}, replyId={}", event.announcementId(), event.replyId());
        ReplyWebSocketEvent signal = new ReplyWebSocketEvent("CREATED", event.replyId(), event.announcementId(), null);
        messagingTemplate.convertAndSend(getTopicPath(event.announcementId()), signal);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpdated(AnnouncementReplyUpdatedEvent event) {
        log.info("Broadcasting reply EDITED event to WS: announcementId={}, replyId={}", event.announcementId(), event.replyId());
        ReplyWebSocketEvent signal = new ReplyWebSocketEvent("EDITED", event.replyId(), event.announcementId(), event.reply());
        messagingTemplate.convertAndSend(getTopicPath(event.announcementId()), signal);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeleted(AnnouncementReplyDeletedEvent event) {
        log.info("Broadcasting reply DELETED event to WS: announcementId={}, replyId={}", event.announcementId(), event.replyId());
        ReplyWebSocketEvent signal = new ReplyWebSocketEvent("DELETED", event.replyId(), event.announcementId(), null);
        messagingTemplate.convertAndSend(getTopicPath(event.announcementId()), signal);
    }

    private String getTopicPath(Long announcementId) {
        return "/topic/announcements/" + announcementId + "/replies";
    }
}
