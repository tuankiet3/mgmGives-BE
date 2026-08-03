package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.event.notification.TaskCreatedEmailEvent;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEmailEventListener {

    private final EmailService emailService;
    private final MailProps mailProps;
    private final UserRepository userRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleTaskCreatedEmail(TaskCreatedEmailEvent event) {
        log.info("Handling task created email event: campaignId={}, taskTitle={}, assigneeCount={}",
                event.campaignId(), event.taskTitle(), event.assignees().size());

        String descriptionSnippet = getPlainTextSnippet(event.taskDescription());
        String formattedDueDate = event.dueDate() != null
                ? event.dueDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : null;

        String taskUrl = UriComponentsBuilder
                .fromUriString(mailProps.getFrontendUrl())
                .pathSegment("campaigns", event.campaignId().toString(), "tasks")
                .toUriString();

        for (NotificationRecipient recipient : event.assignees()) {
            try {
                String assigneeName = userRepository.findById(recipient.userId())
                        .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getEmail())
                        .orElse(recipient.email());

                emailService.sendTaskAssignmentEmail(
                        recipient.email(),
                        assigneeName,
                        event.campaignName(),
                        event.taskTitle(),
                        descriptionSnippet,
                        formattedDueDate,
                        taskUrl
                );
            } catch (Exception e) {
                log.error("Failed to send task assignment email to={}", recipient.email(), e);
            }
        }
    }

    private String getPlainTextSnippet(String html) {
        if (html == null) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        if (text.length() > 120) {
            return text.substring(0, 117) + "...";
        }
        return text;
    }
}
