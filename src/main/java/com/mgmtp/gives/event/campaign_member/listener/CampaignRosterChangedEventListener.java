package com.mgmtp.gives.event.campaign_member.listener;

import com.mgmtp.gives.event.campaign_member.CampaignRosterChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignRosterChangedEventListener {
    private static final String DESTINATION_TEMPLATE = "/topic/campaigns/%d/roster";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRosterChanged(CampaignRosterChangedEvent event) {
        messagingTemplate.convertAndSend(
                String.format(DESTINATION_TEMPLATE, event.campaignId()), event);
        log.debug("Published roster change: campaignId={}", event.campaignId());
    }
}
