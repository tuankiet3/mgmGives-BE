package com.mgmtp.gives.scheduler;

import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMeetingRepository;
import com.mgmtp.gives.service.CampaignMeetingClock;
import com.mgmtp.gives.service.CampaignMeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignMeetingStatusScheduler {
    private final CampaignMeetingService campaignMeetingService;
    private final CampaignMeetingRepository campaignMeetingRepository;
    private final CampaignMeetingClock campaignMeetingClock;

    @Scheduled(cron = "0 * * * * *")
    public void updateMeetingStatus() {
        long startedAt = System.currentTimeMillis();
        LocalDateTime now = campaignMeetingClock.now();
        int scheduledEnded = markScheduledMeetingsEnded(now);
        int scheduledInProgress = markScheduledMeetingsInProgress(now);
        List<CampaignMeeting> meetings = campaignMeetingRepository.findWebexMeetingsByStatus(
                CampaignMeetingStatus.UPCOMING, CampaignMeetingStatus.IN_PROGRESS);

        if (meetings.isEmpty()) {
            log.debug(
                    "No campaign meetings need Webex status sync. scheduledInProgress={}, scheduledEnded={}",
                    scheduledInProgress, scheduledEnded
            );
            return;
        }

        int updated = 0;
        int unchanged = 0;
        int failed = 0;

        log.info("Starting campaign meeting status sync. candidateCount={}", meetings.size());

        for (CampaignMeeting meeting : meetings) {
            try {
                Long campaignId = meeting.getCampaign().getId();
                User campaignAdmin = meeting.getCampaign().getUser();
                CampaignMeetingStatus previousStatus = meeting.getStatus();

                var response = campaignMeetingService.updateMeetingStatus(campaignId, meeting.getId(), campaignAdmin);

                if (previousStatus != response.status()) {
                    updated++;
                    log.info(
                            "Campaign meeting status synced. meetingId={}, campaignId={}, webexMeetingId={}, previousStatus={}, newStatus={}",
                            meeting.getId(), campaignId, meeting.getWebexMeetingId(), previousStatus, response.status()
                    );
                } else {
                    unchanged++;
                    log.debug(
                            "Campaign meeting status unchanged. meetingId={}, campaignId={}, webexMeetingId={}, status={}",
                            meeting.getId(), campaignId, meeting.getWebexMeetingId(), previousStatus
                    );
                }
            } catch (AppException e) {
                failed++;
                log.warn(
                        "Campaign meeting status sync skipped. meetingId={}, webexMeetingId={}, errorCode={}, message={}",
                        meeting.getId(), meeting.getWebexMeetingId(), e.getErrorCode(), e.getMessage(), e
                );
            } catch (Exception e) {
                failed++;
                log.error(
                        "Unexpected error while syncing campaign meeting status. meetingId={}, webexMeetingId={}",
                        meeting.getId(), meeting.getWebexMeetingId(), e
                );
            }
        }

        log.info(
                "Finished campaign meeting status sync. webexCandidateCount={}, webexUpdated={}, webexUnchanged={}, webexFailed={}, scheduledInProgress={}, scheduledEnded={}, durationMs={}",
                meetings.size(), updated, unchanged, failed, scheduledInProgress, scheduledEnded,
                System.currentTimeMillis() - startedAt
        );
    }

    private int markScheduledMeetingsEnded(LocalDateTime now) {
        int updated = campaignMeetingRepository.markScheduledMeetingsEnded(now);
        if (updated > 0) {
            log.info("Scheduled campaign meetings marked ended. count={}", updated);
        }
        return updated;
    }

    private int markScheduledMeetingsInProgress(LocalDateTime now) {
        int updated = campaignMeetingRepository.markScheduledMeetingsInProgress(now);
        if (updated > 0) {
            log.info("Scheduled campaign meetings marked in progress. count={}", updated);
        }
        return updated;
    }
}
