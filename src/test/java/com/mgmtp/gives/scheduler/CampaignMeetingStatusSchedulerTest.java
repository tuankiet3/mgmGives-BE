package com.mgmtp.gives.scheduler;

import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import com.mgmtp.gives.repository.CampaignMeetingRepository;
import com.mgmtp.gives.service.CampaignMeetingClock;
import com.mgmtp.gives.service.CampaignMeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignMeetingStatusSchedulerTest {

    @Mock
    private CampaignMeetingService campaignMeetingService;

    @Mock
    private CampaignMeetingRepository campaignMeetingRepository;

    @Mock
    private CampaignMeetingClock campaignMeetingClock;

    @InjectMocks
    private CampaignMeetingStatusScheduler scheduler;

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 30);

    @BeforeEach
    void setUp() {
        when(campaignMeetingClock.now()).thenReturn(now);
    }

    @Test
    void updateMeetingStatus_AdvancesScheduledMeetingsBeforeWebexSync() {
        CampaignMeeting webexMeeting = webexMeeting();
        when(campaignMeetingRepository.markScheduledMeetingsEnded(now)).thenReturn(1);
        when(campaignMeetingRepository.markScheduledMeetingsInProgress(now)).thenReturn(2);
        when(campaignMeetingRepository.findWebexMeetingsByStatus(
                CampaignMeetingStatus.UPCOMING,
                CampaignMeetingStatus.IN_PROGRESS)).thenReturn(List.of(webexMeeting));
        when(campaignMeetingService.updateMeetingStatus(10L, 100L, webexMeeting.getCampaign().getUser()))
                .thenReturn(CampaignMeetingResponse.builder()
                        .id(100L)
                        .status(CampaignMeetingStatus.IN_PROGRESS)
                        .build());

        scheduler.updateMeetingStatus();

        verify(campaignMeetingRepository).markScheduledMeetingsEnded(now);
        verify(campaignMeetingRepository).markScheduledMeetingsInProgress(now);
        verify(campaignMeetingRepository).findWebexMeetingsByStatus(
                CampaignMeetingStatus.UPCOMING,
                CampaignMeetingStatus.IN_PROGRESS);
        verify(campaignMeetingService).updateMeetingStatus(10L, 100L, webexMeeting.getCampaign().getUser());
    }

    private CampaignMeeting webexMeeting() {
        User owner = new User();
        owner.setId(1L);

        Campaign campaign = new Campaign();
        campaign.setId(10L);
        campaign.setUser(owner);

        CampaignMeeting meeting = new CampaignMeeting();
        meeting.setId(100L);
        meeting.setCampaign(campaign);
        meeting.setWebexMeetingId("webex-id");
        meeting.setStatus(CampaignMeetingStatus.UPCOMING);
        return meeting;
    }
}
