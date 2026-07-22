package com.mgmtp.gives.scheduler;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignScheduler {

    private final CampaignRepository campaignRepository;
    private final CampaignService campaignService;

    // Runs daily at 3 AM to clean up old rejected campaigns
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupRejectedCampaigns() {
        log.info("Rejected campaigns cleanup started");
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            List<Campaign> expiredCampaigns = campaignRepository.findByStatusAndUpdatedAtBefore(
                    CampaignStatus.REJECTED, cutoff);

            log.info("Campaign cleanup found {} expired rejected campaigns", expiredCampaigns.size());

            int deleted = 0;
            for (Campaign campaign : expiredCampaigns) {
                try {
                    campaignService.deleteCampaign(campaign.getId(), null); // Pass null as currentUser for system bypass
                    log.info("Auto-deleted rejected campaign id={}, title='{}'", campaign.getId(), campaign.getTitle());
                    deleted++;
                } catch (Exception e) {
                    log.error("Failed to auto-delete campaign id={}: {}", campaign.getId(), e.getMessage(), e);
                }
            }

            log.info("Rejected campaigns cleanup completed: {} deleted", deleted);
        } catch (Exception e) {
            log.error("Error during rejected campaigns cleanup", e);
        }
    }

    // Runs daily at 00:01 AM to transition APPROVED campaigns starting today to IN_PROGRESS
    @Scheduled(cron = "0 1 0 * * *")
    public void startScheduledCampaigns() {
        log.info("Start scheduled campaigns task triggered");
        try {
            campaignService.startApprovedCampaignsScheduled();
        } catch (Exception e) {
            log.error("Error during auto-starting approved campaigns", e);
        }
    }

    // Runs daily at 00:05 AM to transition IN_PROGRESS campaigns past their end date to COMPLETED
    @Scheduled(cron = "0 5 0 * * *")
    public void completeEndedCampaigns() {
        log.info("Complete ended campaigns task triggered");
        try {
            campaignService.completeEndedCampaignsScheduled();
        } catch (Exception e) {
            log.error("Error during auto-completing ended campaigns", e);
        }
    }
}
