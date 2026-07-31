package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.event.notification.CampaignStatusChangedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyCreatedEvent;
import com.mgmtp.gives.event.notification.TaskAssignedEvent;
import com.mgmtp.gives.event.notification.TaskDescriptionUpdatedEvent;
import com.mgmtp.gives.event.notification.TaskUnassignedEvent;
import com.mgmtp.gives.event.notification.TaskStatusChangedEvent;
import com.mgmtp.gives.notification.NotificationRecipientResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCommandFactoryImplTest {

    @Mock
    private NotificationRecipientResolver recipientResolver;

    @InjectMocks
    private NotificationCommandFactoryImpl notificationCommandFactory;

    @Test
    void campaignStatusChanged_whenPendingToApproved_notifiesCampaignOwnerOnly() {
        NotificationRecipient owner = new NotificationRecipient(1L, "owner@example.com");
        when(recipientResolver.campaignOwner(100L)).thenReturn(Set.of(owner));

        CreateNotificationCommand command = notificationCommandFactory.campaignStatusChanged(
                new CampaignStatusChangedEvent(
                        100L,
                        "Clean Water",
                        CampaignStatus.PENDING,
                        CampaignStatus.APPROVED
                )
        );

        assertEquals(Set.of(owner), command.recipients());
        verify(recipientResolver).campaignOwner(100L);
        verify(recipientResolver, never()).campaignOwnerAndFollowers(100L);
    }

    @Test
    void campaignStatusChanged_whenPendingToInProgress_notifiesCampaignOwnerOnly() {
        NotificationRecipient owner = new NotificationRecipient(1L, "owner@example.com");
        when(recipientResolver.campaignOwner(100L)).thenReturn(Set.of(owner));

        CreateNotificationCommand command = notificationCommandFactory.campaignStatusChanged(
                new CampaignStatusChangedEvent(
                        100L,
                        "Clean Water",
                        CampaignStatus.PENDING,
                        CampaignStatus.IN_PROGRESS
                )
        );

        assertEquals(Set.of(owner), command.recipients());
        verify(recipientResolver).campaignOwner(100L);
        verify(recipientResolver, never()).campaignOwnerAndFollowers(100L);
    }

    @Test
    void campaignStatusChanged_afterCampaignHasBeenApproved_notifiesCampaignOwnerAndFollowers() {
        Set<NotificationRecipient> recipients = Set.of(
                new NotificationRecipient(1L, "owner@example.com"),
                new NotificationRecipient(2L, "follower@example.com")
        );
        when(recipientResolver.campaignOwnerAndFollowers(100L)).thenReturn(recipients);

        CreateNotificationCommand command = notificationCommandFactory.campaignStatusChanged(
                new CampaignStatusChangedEvent(
                        100L,
                        "Clean Water",
                        CampaignStatus.APPROVED,
                        CampaignStatus.IN_PROGRESS
                )
        );

        assertEquals(recipients, command.recipients());
        verify(recipientResolver).campaignOwnerAndFollowers(100L);
        verify(recipientResolver, never()).campaignOwner(100L);
    }

    @Test
    void taskAssigned_createsCorrectNotificationCommand() {
        NotificationRecipient assignee1 = new NotificationRecipient(2L, "assignee1@example.com");
        NotificationRecipient assignee2 = new NotificationRecipient(3L, "assignee2@example.com");
        Set<NotificationRecipient> assignees = Set.of(assignee1, assignee2);

        String title = "Complete the report";

        CreateNotificationCommand command = notificationCommandFactory.taskAssigned(
                new TaskAssignedEvent(
                        4L,
                        10L,
                        title,
                        "Desc",
                        assignees
                )
        );

        assertEquals(assignees, command.recipients());
        assertEquals(NotificationType.TASK_ASSIGNED, command.type());
        assertEquals("New task assigned", command.title());
        assertEquals("You have been assigned to task \"Complete the report\".", command.message());
        assertEquals("/campaigns/4/tasks", command.linkUrl());
    }

    @Test
    void taskDescriptionUpdated_createsCorrectNotificationCommand() {
        NotificationRecipient assignee = new NotificationRecipient(2L, "assignee@example.com");
        Set<NotificationRecipient> recipients = Set.of(assignee);

        CreateNotificationCommand command = notificationCommandFactory.taskDescriptionUpdated(
                new TaskDescriptionUpdatedEvent(
                        4L,
                        10L,
                        "Report Task",
                        recipients
                )
        );

        assertEquals(recipients, command.recipients());
        assertEquals(NotificationType.TASK_ASSIGNED, command.type());
        assertEquals("Task description updated", command.title());
        assertEquals("The description of task \"Report Task\" has been updated.", command.message());
        assertEquals("/campaigns/4/tasks", command.linkUrl());
    }

    @Test
    void taskUnassigned_createsCorrectNotificationCommand() {
        NotificationRecipient assignee = new NotificationRecipient(2L, "assignee@example.com");

        CreateNotificationCommand command = notificationCommandFactory.taskUnassigned(
                new TaskUnassignedEvent(
                        4L,
                        10L,
                        "Report Task",
                        assignee
                )
        );

        assertEquals(Set.of(assignee), command.recipients());
        assertEquals(NotificationType.TASK_ASSIGNED, command.type());
        assertEquals("Task assignment removed", command.title());
        assertEquals("You have been unassigned from task \"Report Task\".", command.message());
        assertEquals("/campaigns/4/tasks", command.linkUrl());
    }

    @Test
    void taskStatusChanged_createsCorrectNotificationCommand() {
        NotificationRecipient assignee = new NotificationRecipient(2L, "assignee@example.com");
        Set<NotificationRecipient> recipients = Set.of(assignee);

        CreateNotificationCommand command = notificationCommandFactory.taskStatusChanged(
                new TaskStatusChangedEvent(
                        4L,
                        10L,
                        "Report Task",
                        com.mgmtp.gives.enums.TaskStatus.TODO,
                        com.mgmtp.gives.enums.TaskStatus.IN_PROGRESS,
                        recipients
                )
        );

        assertEquals(recipients, command.recipients());
        assertEquals(NotificationType.TASK_ASSIGNED, command.type());
        assertEquals("Task status updated", command.title());
        assertEquals("Task \"Report Task\" status has been changed from To do to In progress.", command.message());
        assertEquals("/campaigns/4/tasks", command.linkUrl());
    }

    @Test
    void announcementReplyCreated_NotifiesPublisherAndReferencedReplyAuthor() {
        NotificationRecipient publisher = new NotificationRecipient(1L, "publisher@example.com");
        NotificationRecipient referencedAuthor = new NotificationRecipient(2L, "commenter@example.com");
        when(recipientResolver.singleUser(1L)).thenReturn(Set.of(publisher));
        when(recipientResolver.singleUser(2L)).thenReturn(Set.of(referencedAuthor));

        List<CreateNotificationCommand> commands = notificationCommandFactory.announcementReplyCreated(
                new AnnouncementReplyCreatedEvent(10L, 20L, "Water update", 30L, 3L, "Linh", 1L, 2L)
        );

        assertEquals(2, commands.size());
        assertEquals(Set.of(referencedAuthor), commands.get(0).recipients());
        assertEquals("New reply to your comment", commands.get(0).title());
        assertEquals(Set.of(publisher), commands.get(1).recipients());
        assertEquals("New reply on \"Water update\"", commands.get(1).title());
        assertEquals(NotificationType.ANNOUNCEMENT_REPLY, commands.get(0).type());
        assertEquals("/campaigns/10/announcements/20?reply=20:30", commands.get(0).linkUrl());
    }

    @Test
    void announcementReplyCreated_DeduplicatesPublisherWhoWasDirectlyRepliedTo() {
        NotificationRecipient publisher = new NotificationRecipient(1L, "publisher@example.com");
        when(recipientResolver.singleUser(1L)).thenReturn(Set.of(publisher));

        List<CreateNotificationCommand> commands = notificationCommandFactory.announcementReplyCreated(
                new AnnouncementReplyCreatedEvent(10L, 20L, "Water update", 30L, 3L, "Linh", 1L, 1L)
        );

        assertEquals(1, commands.size());
        assertEquals("New reply to your comment", commands.getFirst().title());
        verify(recipientResolver).singleUser(1L);
    }

    @Test
    void announcementReplyCreated_ExcludesReplyAuthorFromEveryRecipientRule() {
        List<CreateNotificationCommand> commands = notificationCommandFactory.announcementReplyCreated(
                new AnnouncementReplyCreatedEvent(10L, 20L, "Water update", 30L, 1L, "Linh", 1L, 1L)
        );

        assertEquals(List.of(), commands);
        verify(recipientResolver, never()).singleUser(anyLong());
    }
}
