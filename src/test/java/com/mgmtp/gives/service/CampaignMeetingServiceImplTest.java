package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMeetingRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.service.impl.CampaignMeetingServiceImpl;
import com.mgmtp.gives.util.CampaignAccessHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignMeetingServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @Mock
    private CampaignMeetingRepository campaignMeetingRepository;

    @Mock
    private CampaignMediaRepository campaignMediaRepository;

    @Mock
    private WebexMeetingClient webexMeetingClient;

    @Mock
    private CampaignMeetingInvitationService campaignMeetingInvitationService;

    @Mock
    private MediaService mediaService;

    @Mock
    private UserWebexConnectionService userWebexConnectionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CampaignAccessHelper campaignAccessHelper;

    @InjectMocks
    private CampaignMeetingServiceImpl campaignMeetingService;

    private Campaign campaign;
    private User systemAdmin;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);

        systemAdmin = new User();
        systemAdmin.setId(2L);
        systemAdmin.setRole(UserRole.ADMIN);

        campaign = new Campaign();
        campaign.setId(10L);
        campaign.setUser(owner);

        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
    }

    @Test
    void getMeetingRecipients_SystemAdminOfAnotherCampaign_ThrowsAppException() {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE))
                .when(campaignAccessHelper).validateCampaignAdmin(
                        eq(campaign.getId()),
                        eq(systemAdmin),
                        eq(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE));

        AppException exception = assertThrows(AppException.class,
                () -> campaignMeetingService.getMeetingRecipients(campaign.getId(), systemAdmin));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, exception.getErrorCode());
        verify(campaignMeetingRepository, never()).findByCampaignIdOrderByStartTimeAsc(campaign.getId());
    }

    @Test
    void getMeetings_SystemAdminWithoutCampaignMembership_ThrowsAppException() {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS))
                .when(campaignAccessHelper).validateCampaignMemberOrAdmin(
                        eq(campaign.getId()),
                        eq(systemAdmin),
                        eq(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS));

        AppException exception = assertThrows(AppException.class,
                () -> campaignMeetingService.getMeetings(campaign.getId(), "all", systemAdmin));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
        verify(campaignMeetingRepository, never()).findByCampaignIdOrderByStartTimeAsc(campaign.getId());
    }
}
