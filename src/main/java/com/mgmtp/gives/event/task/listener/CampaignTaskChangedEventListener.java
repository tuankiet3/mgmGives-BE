package com.mgmtp.gives.event.task.listener;

import com.mgmtp.gives.event.task.CampaignTaskChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignTaskChangedEventListener {
    private static final String DESTINATION = "/queue/task-updates";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(CampaignTaskChangedEvent event) {
        event.recipientEmails().forEach(email ->
                messagingTemplate.convertAndSendToUser(email, DESTINATION, event.payload()));
        log.debug("Published task change: action={}, taskId={}, version={}, recipients={}",
                event.payload().action(), event.payload().taskId(), event.payload().version(),
                event.recipientEmails().size());
    }
}
