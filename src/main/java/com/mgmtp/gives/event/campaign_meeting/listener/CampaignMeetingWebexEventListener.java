package com.mgmtp.gives.event.campaign_meeting.listener;

import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingWebexCancellationEvent;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.UserWebexConnectionService;
import com.mgmtp.gives.service.WebexMeetingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignMeetingWebexEventListener {
    private final UserRepository userRepository;
    private final UserWebexConnectionService userWebexConnectionService;
    private final WebexMeetingClient webexMeetingClient;

    @Async("campaignMeetingTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleWebexCancellation(CampaignMeetingWebexCancellationEvent event) {
        if (!StringUtils.hasText(event.webexMeetingId()) || event.hostUserId() == null) {
            log.warn("Webex cancellation skipped. meetingId={}, webexMeetingId={}, hostUserId={}",
                    event.meetingId(), event.webexMeetingId(), event.hostUserId());
            return;
        }

        try {
            userRepository.findById(event.hostUserId()).ifPresentOrElse(hostUser -> {
                String accessToken = userWebexConnectionService.getValidAccessToken(hostUser);
                webexMeetingClient.cancelMeeting(event.webexMeetingId(), accessToken);
                log.info("Webex meeting cancelled asynchronously. meetingId={}, webexMeetingId={}",
                        event.meetingId(), event.webexMeetingId());
            }, () -> log.warn("Webex cancellation skipped because host user was not found. meetingId={}, hostUserId={}",
                    event.meetingId(), event.hostUserId()));
        } catch (Exception e) {
            log.error("Failed to cancel Webex meeting asynchronously. meetingId={}, webexMeetingId={}",
                    event.meetingId(), event.webexMeetingId(), e);
        }
    }
}
