package com.mgmtp.gives.event.task.listener;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskChangeAction;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskChangedPayload;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.event.task.CampaignTaskChangedEvent;
import com.mgmtp.gives.event.task.CampaignTaskConflictEvent;
import com.mgmtp.gives.service.CampaignTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignTaskChangedEventListener {
    private static final String DESTINATION = "/queue/task-updates";

    private final SimpMessagingTemplate messagingTemplate;
    private final CampaignTaskService campaignTaskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(CampaignTaskChangedEvent event) {
        event.recipientEmails().forEach(email ->
                messagingTemplate.convertAndSendToUser(email, DESTINATION, event.payload()));
        log.debug("Published task change: action={}, taskId={}, version={}, recipients={}",
                event.payload().action(), event.payload().taskId(), event.payload().version(),
                event.recipientEmails().size());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTaskConflict(CampaignTaskConflictEvent event) {
        if (event.recipient() == null || event.recipient().getEmail() == null
                || event.recipient().getEmail().isBlank()) {
            return;
        }

        try {
            CampaignTaskChangedPayload payload = event.payload();
            if (payload == null) {
                CampaignTaskResponse currentTask = campaignTaskService.getTaskById(event.taskId(), event.recipient());
                payload = new CampaignTaskChangedPayload(
                        "TASK_CHANGED",
                        CampaignTaskChangeAction.UPDATED,
                        currentTask.campaignId(),
                        currentTask.id(),
                        currentTask.version(),
                        currentTask,
                        currentTask.updatedAt(),
                        event.recipient().getId());
            }

            messagingTemplate.convertAndSendToUser(event.recipient().getEmail(), DESTINATION, payload);
            log.debug("Published task conflict snapshot: taskId={}, version={}, recipient={}",
                    payload.taskId(), payload.version(), event.recipient().getEmail());
        } catch (RuntimeException ex) {
            log.warn("Failed to publish task conflict snapshot: taskId={}, recipientId={}",
                    event.taskId(), event.recipient().getId(), ex);
        }
    }
}
