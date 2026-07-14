package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.announcement.AnnouncementReplyResponse;
import com.mgmtp.gives.dto.announcement.CreateReplyRequest;
import com.mgmtp.gives.dto.announcement.ReplyPageResponse;
import com.mgmtp.gives.dto.announcement.UpdateReplyRequest;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.AnnouncementReply;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.AnnouncementReplyMapper;
import com.mgmtp.gives.repository.AnnouncementReplyRepository;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.security.AnnouncementAccessAuthorizer;
import com.mgmtp.gives.service.impl.AnnouncementReplyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementReplyServiceImplTest {

    @Mock
    private AnnouncementReplyRepository replyRepository;

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private AnnouncementReplyMapper replyMapper;

    private AnnouncementReplyServiceImpl replyService;

    private User author;
    private User creator;
    private User admin;
    private Campaign campaign;
    private Announcement announcement;
    private AnnouncementReply reply;

    @BeforeEach
    void setUp() {
        author = new User();
        author.setId(10L);
        author.setFullName("Reply Author");
        author.setEmail("author@test.com");
        author.setRole(UserRole.USER);

        creator = new User();
        creator.setId(20L);
        creator.setFullName("Campaign Creator");
        creator.setEmail("creator@test.com");
        creator.setRole(UserRole.USER);

        admin = new User();
        admin.setId(30L);
        admin.setFullName("Admin User");
        admin.setEmail("admin@test.com");
        admin.setRole(UserRole.ADMIN);

        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setUser(creator);

        announcement = new Announcement();
        announcement.setId(5L);
        announcement.setCampaign(campaign);
        announcement.setRepliesCount(0);

        reply = new AnnouncementReply();
        reply.setId(100L);
        reply.setAnnouncement(announcement);
        reply.setUser(author);
        reply.setContent("Original Content");
        reply.setEdited(false);
        reply.setVersion(0L);

        lenient().when(replyMapper.toResponse(any(AnnouncementReply.class))).thenAnswer(inv -> {
            AnnouncementReply r = inv.getArgument(0);
            if (r == null) return null;
            return new AnnouncementReplyResponse(
                    r.getId(),
                    r.getAnnouncement() != null ? r.getAnnouncement().getId() : null,
                    r.getContent(),
                    r.getUser() == null ? null : new AnnouncementReplyResponse.UserSummary(
                            r.getUser().getId(),
                            r.getUser().getFullName(),
                            r.getUser().getAvatarUrl()
                    ),
                    r.isEdited(),
                    r.getCreatedAt(),
                    r.getUpdatedAt()
            );
        });
        lenient().when(announcementRepository.findByIdAndCampaignId(5L, 1L)).thenReturn(Optional.of(announcement));
        replyService = new AnnouncementReplyServiceImpl(
                replyRepository,
                announcementRepository,
                replyMapper,
                new AnnouncementAccessAuthorizer(announcementRepository)
        );
    }

    @Test
    void createReply_Success() {
        CreateReplyRequest request = new CreateReplyRequest("Reply Content");
        when(replyRepository.save(any(AnnouncementReply.class))).thenReturn(reply);

        AnnouncementReplyResponse result = replyService.createReply(1L, 5L, request, author);

        assertNotNull(result);
        assertEquals("Original Content", result.content());
        verify(announcementRepository, times(1)).incrementRepliesCount(5L);
    }

    @Test
    void createReply_WrongCampaign_ThrowsException() {
        CreateReplyRequest request = new CreateReplyRequest("Reply Content");
        when(announcementRepository.findByIdAndCampaignId(5L, 99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                replyService.createReply(99L, 5L, request, author)
        );

        assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void updateReply_Success() {
        UpdateReplyRequest request = new UpdateReplyRequest("Updated Content");
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));
        when(replyRepository.save(any(AnnouncementReply.class))).thenAnswer(inv -> inv.getArgument(0));

        AnnouncementReplyResponse result = replyService.updateReply(1L, 5L, 100L, request, author);

        assertNotNull(result);
        assertTrue(result.isEdited());
        assertEquals("Updated Content", result.content());
    }

    @Test
    void updateReply_NoChanges_DoesNotFlagEdited() {
        UpdateReplyRequest request = new UpdateReplyRequest("Original Content");
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));

        AnnouncementReplyResponse result = replyService.updateReply(1L, 5L, 100L, request, author);

        assertNotNull(result);
        assertFalse(result.isEdited());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void updateReply_Unauthorized_ThrowsException() {
        UpdateReplyRequest request = new UpdateReplyRequest("Updated Content");
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));

        AppException exception = assertThrows(AppException.class, () ->
                replyService.updateReply(1L, 5L, 100L, request, creator)
        );

        assertEquals(ErrorCode.UNAUTHORIZED_REPLY_ACTION, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void updateReply_ReplyOutsideAnnouncement_ThrowsException() {
        UpdateReplyRequest request = new UpdateReplyRequest("Updated Content");
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                replyService.updateReply(1L, 5L, 100L, request, author)
        );

        assertEquals(ErrorCode.REPLY_NOT_FOUND, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
    }

    @Test
    void deleteReply_Author_Success() {
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));

        replyService.deleteReply(1L, 5L, 100L, author);

        assertNotNull(reply.getDeletedAt());
        assertEquals("AUTHOR", reply.getDeletedByRole());
        assertEquals(author, reply.getDeletedBy());
        verify(announcementRepository, times(1)).decrementRepliesCount(5L);
    }

    @Test
    void deleteReply_CampaignCreator_Success() {
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));

        replyService.deleteReply(1L, 5L, 100L, creator);

        assertNotNull(reply.getDeletedAt());
        assertEquals("CAMPAIGN_CREATOR", reply.getDeletedByRole());
        assertEquals(creator, reply.getDeletedBy());
        verify(announcementRepository, times(1)).decrementRepliesCount(5L);
    }

    @Test
    void deleteReply_Admin_Success() {
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));

        replyService.deleteReply(1L, 5L, 100L, admin);

        assertNotNull(reply.getDeletedAt());
        assertEquals("ADMIN", reply.getDeletedByRole());
        assertEquals(admin, reply.getDeletedBy());
        verify(announcementRepository, times(1)).decrementRepliesCount(5L);
    }

    @Test
    void deleteReply_Unauthorized_ThrowsException() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setRole(UserRole.USER);
        when(replyRepository.findByIdAndAnnouncementId(100L, 5L)).thenReturn(Optional.of(reply));

        AppException exception = assertThrows(AppException.class, () ->
                replyService.deleteReply(1L, 5L, 100L, otherUser)
        );

        assertEquals(ErrorCode.UNAUTHORIZED_REPLY_ACTION, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
        verify(announcementRepository, never()).decrementRepliesCount(anyLong());
    }

    @Test
    void getReplies_Ascending_HasMore() {
        List<AnnouncementReply> list = new ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            AnnouncementReply r = new AnnouncementReply();
            r.setId(i);
            r.setAnnouncement(announcement);
            r.setContent("Content " + i);
            r.setUser(author);
            list.add(r);
        }

        when(replyRepository.findRepliesAscendingWithCursor(eq(5L), eq(10L), any(Pageable.class))).thenReturn(list);

        ReplyPageResponse<AnnouncementReplyResponse> response = replyService.getReplies(1L, 5L, 10L, 5, "asc", author);

        assertNotNull(response);
        assertEquals(5, response.content().size());
        assertEquals(5L, response.nextCursor());
    }

    @Test
    void getReplies_Descending_NoMore() {
        List<AnnouncementReply> list = new ArrayList<>();
        for (long i = 3; i >= 1; i--) {
            AnnouncementReply r = new AnnouncementReply();
            r.setId(i);
            r.setAnnouncement(announcement);
            r.setContent("Content " + i);
            r.setUser(author);
            list.add(r);
        }

        when(replyRepository.findRepliesDescendingWithCursor(eq(5L), eq(10L), any(Pageable.class))).thenReturn(list);

        ReplyPageResponse<AnnouncementReplyResponse> response = replyService.getReplies(1L, 5L, 10L, 5, "desc", author);

        assertNotNull(response);
        assertEquals(3, response.content().size());
        assertNull(response.nextCursor());
    }
}
