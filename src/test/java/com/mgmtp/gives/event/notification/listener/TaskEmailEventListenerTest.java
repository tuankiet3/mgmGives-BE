package com.mgmtp.gives.event.notification.listener;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.event.notification.TaskCreatedEmailEvent;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskEmailEventListenerTest {

    @Mock EmailService emailService;
    @Mock MailProps mailProps;
    @Mock UserRepository userRepository;

    @InjectMocks TaskEmailEventListener listener;

    @Test
    void handleTaskCreatedEmail_sendsEmailsToAssignees() {
        when(mailProps.getFrontendUrl()).thenReturn("http://localhost:3000");

        NotificationRecipient recipient = new NotificationRecipient(2L, "assignee@example.com");
        User assignee = new User();
        assignee.setId(2L);
        assignee.setFullName("John Doe");
        assignee.setEmail("assignee@example.com");

        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));

        TaskCreatedEmailEvent event = new TaskCreatedEmailEvent(
                4L,
                "mgmGives Campaign",
                "New Task",
                "HTML <p>Description</p>",
                LocalDateTime.of(2026, 7, 20, 23, 59),
                Set.of(recipient)
        );

        listener.handleTaskCreatedEmail(event);

        verify(emailService).sendTaskAssignmentEmail(
                eq("assignee@example.com"),
                eq("John Doe"),
                eq("mgmGives Campaign"),
                eq("New Task"),
                eq("HTML Description"),
                eq("2026-07-20 23:59"),
                eq("http://localhost:3000/campaigns/4/tasks")
        );
    }
}
