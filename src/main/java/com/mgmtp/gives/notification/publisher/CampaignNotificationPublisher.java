package com.mgmtp.gives.notification.publisher;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.event.notification.CampaignStatusChangedEvent;
import com.mgmtp.gives.event.notification.CampaignUnjoinApprovedEvent;
import com.mgmtp.gives.event.notification.CampaignUnjoinRejectedEvent;
import com.mgmtp.gives.event.notification.CampaignUnjoinRequestedEvent;
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

    public void publishUnjoinRequested(Campaign campaign, User requester) {
        eventPublisher.publishEvent(
                new CampaignUnjoinRequestedEvent(
                        campaign.getId(),
                        campaign.getTitle(),
                        requester.getId(),
                        requester.getFullName()
                )
        );
    }

    public void publishUnjoinApproved(Campaign campaign, User user) {
        eventPublisher.publishEvent(
                new CampaignUnjoinApprovedEvent(
                        campaign.getId(),
                        campaign.getTitle(),
                        user.getId()
                )
        );
    }

    public void publishUnjoinRejected(Campaign campaign, User user, String reason) {
        eventPublisher.publishEvent(
                new CampaignUnjoinRejectedEvent(
                        campaign.getId(),
                        campaign.getTitle(),
                        user.getId(),
                        reason
                )
        );
    }
}
