package com.mgmtp.gives.event.campaign_meeting.listener;

import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingCancellationEmailEvent;
import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingInvitationEmailEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CampaignMeetingEmailEventListenerTest {

    @Test
    void meetingEmailHandlers_RunOnlyAfterCommit() throws Exception {
        TransactionalEventListener invitation = CampaignMeetingEmailEventListener.class
                .getDeclaredMethod("handleInvitation", CampaignMeetingInvitationEmailEvent.class)
                .getAnnotation(TransactionalEventListener.class);
        TransactionalEventListener cancellation = CampaignMeetingEmailEventListener.class
                .getDeclaredMethod("handleCancellation", CampaignMeetingCancellationEmailEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertNotNull(invitation);
        assertNotNull(cancellation);
        assertEquals(TransactionPhase.AFTER_COMMIT, invitation.phase());
        assertEquals(TransactionPhase.AFTER_COMMIT, cancellation.phase());
        assertFalse(invitation.fallbackExecution());
        assertFalse(cancellation.fallbackExecution());
    }
}
