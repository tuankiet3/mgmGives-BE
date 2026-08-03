package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.mapper.CampaignMemberMapper;
import com.mgmtp.gives.notification.publisher.CampaignNotificationPublisher;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.repository.TaskAssignmentRepository;
import com.mgmtp.gives.service.impl.CampaignMemberServiceImpl;
import com.mgmtp.gives.util.CampaignAccessHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignMemberServiceImplTest {

    @Mock
    private CampaignMemberRepository campaignMemberRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignFollowerService campaignFollowerService;
    @Mock
    private CampaignMemberMapper campaignMemberMapper;
    @Mock
    private DonationRepository donationRepository;
    @Mock
    private CampaignMediaRepository campaignMediaRepository;
    @Mock
    private TaskAssignmentRepository taskAssignmentRepository;
    @Mock
    private CampaignAccessHelper campaignAccessHelper;
    @Mock
    private CampaignNotificationPublisher campaignNotificationPublisher;

    @InjectMocks
    private CampaignMemberServiceImpl service;

    @Test
    void canManageCampaignAllowsGlobalAdminWithoutMembershipLookup() {
        User admin = User.builder().id(7L).role(UserRole.ADMIN).build();

        assertTrue(service.canManageCampaign(42L, admin));
        verifyNoInteractions(campaignMemberRepository);
    }

    @Test
    void canManageCampaignAllowsCampaignAdminMember() {
        User user = User.builder().id(8L).role(UserRole.USER).build();
        when(campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                42L, 8L, CampaignMemberRole.CAMPAIGN_ADMIN)).thenReturn(true);

        assertTrue(service.canManageCampaign(42L, user));
        verify(campaignMemberRepository).existsByCampaignIdAndUserIdAndRoleInCampaign(
                42L, 8L, CampaignMemberRole.CAMPAIGN_ADMIN);
    }

    @Test
    void canManageCampaignRejectsRegularNonMember() {
        User user = User.builder().id(9L).role(UserRole.USER).build();

        assertFalse(service.canManageCampaign(42L, user));
    }

    @Test
    void canManageCampaignRejectsMissingUser() {
        assertFalse(service.canManageCampaign(42L, null));
        verifyNoInteractions(campaignMemberRepository);
    }
}
