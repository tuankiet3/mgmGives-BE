package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.impl.MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    @Mock
    private CampaignMediaRepository campaignMediaRepository;

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MediaServiceImpl mediaService;

    private User owner;
    private Campaign campaign;
    private CampaignMedia coverMedia;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaService, "uploadDir", "uploads");

        owner = new User();
        owner.setId(1L);

        campaign = new Campaign();
        campaign.setId(10L);
        campaign.setUser(owner);

        coverMedia = new CampaignMedia();
        coverMedia.setId(100L);
        coverMedia.setCampaign(campaign);
        coverMedia.setCover(true);
        coverMedia.setUrl("cover.jpg");
    }

    @Test
    void softDeleteCampaignMedia_Cover_DraftStatus_Success() {
        campaign.setStatus(CampaignStatus.DRAFT);
        when(campaignMediaRepository.findById(100L)).thenReturn(Optional.of(coverMedia));

        CampaignMediaResponse response = mediaService.softDeleteCampaignMedia(100L, owner);

        assertNotNull(response);
        assertEquals(coverMedia.getId(), response.getId());
        assertNotNull(coverMedia.getDeletedAt());
        verify(campaignMediaRepository, times(1)).save(coverMedia);
    }

    @Test
    void softDeleteCampaignMedia_Cover_PendingStatus_Success() {
        campaign.setStatus(CampaignStatus.PENDING);
        when(campaignMediaRepository.findById(100L)).thenReturn(Optional.of(coverMedia));

        CampaignMediaResponse response = mediaService.softDeleteCampaignMedia(100L, owner);

        assertNotNull(response);
        assertEquals(coverMedia.getId(), response.getId());
        assertNotNull(coverMedia.getDeletedAt());
        verify(campaignMediaRepository, times(1)).save(coverMedia);
    }

    @Test
    void softDeleteCampaignMedia_Cover_RejectedStatus_Success() {
        campaign.setStatus(CampaignStatus.REJECTED);
        when(campaignMediaRepository.findById(100L)).thenReturn(Optional.of(coverMedia));

        CampaignMediaResponse response = mediaService.softDeleteCampaignMedia(100L, owner);

        assertNotNull(response);
        assertEquals(coverMedia.getId(), response.getId());
        assertNotNull(coverMedia.getDeletedAt());
        verify(campaignMediaRepository, times(1)).save(coverMedia);
    }

    @Test
    void softDeleteCampaignMedia_Cover_ApprovedStatus_ThrowsAppException() {
        campaign.setStatus(CampaignStatus.APPROVED);
        when(campaignMediaRepository.findById(100L)).thenReturn(Optional.of(coverMedia));

        AppException exception = assertThrows(AppException.class, () -> 
                mediaService.softDeleteCampaignMedia(100L, owner));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        assertEquals("Cannot remove cover photo directly", exception.getMessage());
        verify(campaignMediaRepository, never()).save(any());
    }

    @Test
    void softDeleteCampaignMedia_SystemAdminOfAnotherCampaign_ThrowsAppException() {
        User systemAdmin = new User();
        systemAdmin.setId(2L);
        systemAdmin.setRole(UserRole.ADMIN);
        campaign.setStatus(CampaignStatus.DRAFT);

        when(campaignMediaRepository.findById(100L)).thenReturn(Optional.of(coverMedia));
        when(campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaign.getId(), systemAdmin.getId(), CampaignMemberRole.CAMPAIGN_ADMIN))
                .thenReturn(false);

        AppException exception = assertThrows(AppException.class,
                () -> mediaService.softDeleteCampaignMedia(100L, systemAdmin));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, exception.getErrorCode());
        verify(campaignMediaRepository, never()).save(any());
    }
}
