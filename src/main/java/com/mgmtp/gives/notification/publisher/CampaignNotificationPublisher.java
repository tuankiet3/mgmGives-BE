package com.mgmtp.gives.notification.publisher;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.event.notification.CampaignStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignNotificationPublisher {
    private final ApplicationEventPublisher eventPublisher;

    public void publishCampaignStatusChanged(Campaign campaign, CampaignStatus oldStatus, CampaignStatus newStatus) {
        if (oldStatus == newStatus) {
            return;
        }

        eventPublisher.publishEvent(
                new CampaignStatusChangedEvent(
                        campaign.getId(),
                        campaign.getTitle(),
                        oldStatus,
                        newStatus
                )
        );
    }
}
