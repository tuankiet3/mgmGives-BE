package com.mgmtp.gives.event.campaign_meeting.listener;

import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingCancellationEmailEvent;
import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingInvitationEmailEvent;
import com.mgmtp.gives.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignMeetingEmailEventListener {
    private final EmailService emailService;

    @Async("campaignMeetingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInvitation(CampaignMeetingInvitationEmailEvent event) {
        try {
            emailService.sendCampaignMeetingCalendarEmail(event.request());
        } catch (Exception e) {
            log.error("Failed to send campaign meeting invitation email. to={}, meetingTitle={}",
                    event.request().toEmail(), event.request().meetingTitle(), e);
        }
    }

    @Async("campaignMeetingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCancellation(CampaignMeetingCancellationEmailEvent event) {
        try {
            emailService.sendCampaignMeetingCalendarEmail(event.request());
        } catch (Exception e) {
            log.error("Failed to send campaign meeting cancellation email. to={}, meetingTitle={}",
                    event.request().toEmail(), event.request().meetingTitle(), e);
        }
    }
}
