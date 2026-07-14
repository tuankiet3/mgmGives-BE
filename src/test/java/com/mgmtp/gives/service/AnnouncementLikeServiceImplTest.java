package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.AnnouncementLikeRepository;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.security.AnnouncementAccessAuthorizer;
import com.mgmtp.gives.service.impl.AnnouncementLikeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementLikeServiceImplTest {

    @Mock
    private AnnouncementLikeRepository likeRepository;

    @Mock
    private AnnouncementRepository announcementRepository;

    private AnnouncementLikeServiceImpl likeService;

    private Announcement testAnnouncement;
    private Long campaignId = 10L;
    private Long announcementId = 1L;
    private User user;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);

        testAnnouncement = new Announcement();
        testAnnouncement.setId(announcementId);
        testAnnouncement.setTitle("Test Title");
        testAnnouncement.setLikesCount(0);
        testAnnouncement.setCampaign(campaign);
        user = new User();
        user.setId(100L);

        lenient().when(announcementRepository.findByIdAndCampaignId(announcementId, campaignId))
                .thenReturn(java.util.Optional.of(testAnnouncement));
        likeService = new AnnouncementLikeServiceImpl(
                likeRepository,
                announcementRepository,
                new AnnouncementAccessAuthorizer(announcementRepository)
        );
    }

    @Test
    void likeAnnouncement_Success() {
        when(likeRepository.insertIgnore(announcementId, user.getId())).thenReturn(1);

        likeService.likeAnnouncement(campaignId, announcementId, user);

        verify(announcementRepository, times(1)).incrementLikesCount(announcementId);
    }

    @Test
    void likeAnnouncement_AlreadyLiked_DoesNotIncrement() {
        when(likeRepository.insertIgnore(announcementId, user.getId())).thenReturn(0);

        likeService.likeAnnouncement(campaignId, announcementId, user);

        verify(announcementRepository, never()).incrementLikesCount(anyLong());
    }

    @Test
    void likeAnnouncement_NotFound_ThrowsException() {
        when(announcementRepository.findByIdAndCampaignId(announcementId, campaignId))
                .thenReturn(java.util.Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                likeService.likeAnnouncement(campaignId, announcementId, user)
        );

        assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, exception.getErrorCode());
        verify(likeRepository, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    void likeAnnouncement_CampaignMismatch_ThrowsException() {
        when(announcementRepository.findByIdAndCampaignId(announcementId, 999L))
                .thenReturn(java.util.Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                likeService.likeAnnouncement(999L, announcementId, user)
        );

        assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, exception.getErrorCode());
        verify(likeRepository, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    void unlikeAnnouncement_Success() {
        when(likeRepository.deleteLike(announcementId, user.getId())).thenReturn(1);

        likeService.unlikeAnnouncement(campaignId, announcementId, user);

        verify(announcementRepository, times(1)).decrementLikesCount(announcementId);
    }

    @Test
    void unlikeAnnouncement_NotLiked_DoesNotDecrement() {
        when(likeRepository.deleteLike(announcementId, user.getId())).thenReturn(0);

        likeService.unlikeAnnouncement(campaignId, announcementId, user);

        verify(announcementRepository, never()).decrementLikesCount(anyLong());
    }

    @Test
    void unlikeAnnouncement_NotFound_ThrowsException() {
        when(announcementRepository.findByIdAndCampaignId(announcementId, campaignId))
                .thenReturn(java.util.Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                likeService.unlikeAnnouncement(campaignId, announcementId, user)
        );

        assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, exception.getErrorCode());
        verify(likeRepository, never()).deleteLike(anyLong(), anyLong());
    }

    @Test
    void unlikeAnnouncement_CampaignMismatch_ThrowsException() {
        when(announcementRepository.findByIdAndCampaignId(announcementId, 999L))
                .thenReturn(java.util.Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                likeService.unlikeAnnouncement(999L, announcementId, user)
        );

        assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, exception.getErrorCode());
        verify(likeRepository, never()).deleteLike(anyLong(), anyLong());
    }
}
