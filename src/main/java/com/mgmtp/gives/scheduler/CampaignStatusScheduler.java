package com.mgmtp.gives.scheduler;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.notification.publisher.CampaignNotificationPublisher;
import com.mgmtp.gives.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignStatusScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignNotificationPublisher publisher;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void updateCampaignStatuses() {
        LocalDateTime now = LocalDateTime.now();

        startApprovedCampaigns(now);
        completeEndedCampaigns(now);
    }

    private void startApprovedCampaigns(LocalDateTime now) {
        List<Campaign> campaigns = campaignRepository.findApprovedCampaignsToStart(now);

        for (Campaign campaign : campaigns) {
            changeStatus(campaign, CampaignStatus.IN_PROGRESS);
        }

        log.info("Started approved campaigns: count={}", campaigns.size());
    }

    private void completeEndedCampaigns(LocalDateTime now) {
        List<Campaign> campaigns = campaignRepository.findInProgressCampaignsToComplete(now);

        for (Campaign campaign : campaigns) {
            changeStatus(campaign, CampaignStatus.COMPLETED);
        }

        log.info("Completed ended campaigns: count={}", campaigns.size());
    }

    private void changeStatus(Campaign campaign, CampaignStatus newStatus) {
        CampaignStatus oldStatus = campaign.getStatus();

        if (oldStatus == newStatus) {
            return;
        }

        campaign.setStatus(newStatus);

        publisher.publishCampaignStatusChanged(
                campaign,
                oldStatus,
                newStatus
        );
    }
}