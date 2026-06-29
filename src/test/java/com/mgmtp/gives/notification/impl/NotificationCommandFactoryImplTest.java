package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.event.notification.CampaignStatusChangedEvent;
import com.mgmtp.gives.notification.NotificationRecipientResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
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
}
