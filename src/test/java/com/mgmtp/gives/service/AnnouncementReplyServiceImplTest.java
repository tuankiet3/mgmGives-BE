package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.announcement.AnnouncementReplyResponse;
import com.mgmtp.gives.dto.announcement.CreateReplyRequest;
import com.mgmtp.gives.dto.announcement.ReplyPageResponse;
import com.mgmtp.gives.dto.announcement.ReplyContextResponse;
import com.mgmtp.gives.dto.announcement.UpdateReplyRequest;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.AnnouncementReply;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.event.notification.AnnouncementReplyCreatedEvent;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.AnnouncementReplyMapper;
import com.mgmtp.gives.repository.AnnouncementReplyRepository;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.security.AnnouncementAccessAuthorizer;
import com.mgmtp.gives.service.impl.AnnouncementReplyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;

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
    private UserRepository userRepository;

    @Mock
    private AnnouncementReplyMapper replyMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        announcement.setCreatedBy(creator);
        announcement.setTitle("Campaign update");
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
                    r.getUpdatedAt(),
                    null
            );
        });
        lenient().when(announcementRepository.findByIdAndCampaignId(5L, 1L)).thenReturn(Optional.of(announcement));
        lenient().when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        replyService = new AnnouncementReplyServiceImpl(
                replyRepository,
                announcementRepository,
                userRepository,
                replyMapper,
                new AnnouncementAccessAuthorizer(announcementRepository),
                eventPublisher
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
        var savedReply = org.mockito.ArgumentCaptor.forClass(AnnouncementReply.class);
        verify(replyRepository).save(savedReply.capture());
        assertSame(author, savedReply.getValue().getUser());
        assertNull(savedReply.getValue().getVersion());

        var event = org.mockito.ArgumentCaptor.forClass(AnnouncementReplyCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(100L, event.getValue().replyId());
        assertEquals(20L, event.getValue().announcementPublisherId());
        assertNull(event.getValue().referencedReplyAuthorId());
    }

    @Test
    void createReply_WithReplyContext_PersistsReference() {
        AnnouncementReply referencedReply = new AnnouncementReply();
        referencedReply.setId(99L);
        referencedReply.setAnnouncement(announcement);
        referencedReply.setUser(creator);

        CreateReplyRequest request = new CreateReplyRequest("Reply Content", 99L);
        when(replyRepository.findByIdAndAnnouncementIdWithUser(99L, 5L)).thenReturn(Optional.of(referencedReply));
        when(replyRepository.save(any(AnnouncementReply.class))).thenReturn(reply);

        replyService.createReply(1L, 5L, request, author);

        var savedReply = org.mockito.ArgumentCaptor.forClass(AnnouncementReply.class);
        verify(replyRepository).save(savedReply.capture());
        assertSame(referencedReply, savedReply.getValue().getInReplyTo());

        var event = org.mockito.ArgumentCaptor.forClass(AnnouncementReplyCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(20L, event.getValue().referencedReplyAuthorId());
    }

    @Test
    void createReply_ReferencedReplyNotFound_ThrowsException() {
        CreateReplyRequest request = new CreateReplyRequest("Reply Content", 99L);
        when(replyRepository.findByIdAndAnnouncementIdWithUser(99L, 5L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                replyService.createReply(1L, 5L, request, author)
        );

        assertEquals(ErrorCode.REPLY_NOT_FOUND, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
        verify(announcementRepository, never()).incrementRepliesCount(anyLong());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createReply_ReferencedReplyOutsideAnnouncement_ThrowsException() {
        CreateReplyRequest request = new CreateReplyRequest("Reply Content", 99L);
        when(replyRepository.findByIdAndAnnouncementIdWithUser(99L, 5L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class, () ->
                replyService.createReply(1L, 5L, request, author)
        );

        assertEquals(ErrorCode.REPLY_NOT_FOUND, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createReply_ReferencedReplyDeleted_ThrowsException() {
        AnnouncementReply deletedReply = new AnnouncementReply();
        deletedReply.setId(99L);
        deletedReply.setAnnouncement(announcement);
        deletedReply.setDeletedAt(java.time.LocalDateTime.now());

        CreateReplyRequest request = new CreateReplyRequest("Reply Content", 99L);
        when(replyRepository.findByIdAndAnnouncementIdWithUser(99L, 5L)).thenReturn(Optional.of(deletedReply));

        AppException exception = assertThrows(AppException.class, () ->
                replyService.createReply(1L, 5L, request, author)
        );

        assertEquals(ErrorCode.REPLY_NOT_FOUND, exception.getErrorCode());
        verify(replyRepository, never()).save(any());
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

    @Test
    void getReplyContext_Initial_ReturnsContiguousWindowAroundAnchor() {
        AnnouncementReply anchor = replyWithId(50L);
        when(replyRepository.findActiveReplyIdsAroundCursor(5L, 50L, 3, 3)).thenReturn(List.of(
                replyContextCandidate(48L, 1), replyContextCandidate(52L, 0),
                replyContextCandidate(49L, 1), replyContextCandidate(51L, 0)
        ));
        when(replyRepository.findActiveRepliesWithDetailsByAnnouncementIdAndIdIn(eq(5L), anyCollection()))
                .thenReturn(List.of(replyWithId(49L), anchor, replyWithId(52L), replyWithId(48L), replyWithId(51L)));

        ReplyContextResponse response = replyService.getReplyContext(1L, 5L, 50L, null, null, 5, "desc", author);

        assertEquals(List.of(52L, 51L, 50L, 49L, 48L), response.content().stream().map(AnnouncementReplyResponse::id).toList());
        assertEquals(52L, response.newerCursor());
        assertEquals(48L, response.olderCursor());
        assertFalse(response.hasNewer());
        assertFalse(response.hasOlder());
    }

    @Test
    void getReplyContext_NewerPage_ReturnsSortedPageAndCursor() {
        AnnouncementReply anchor = replyWithId(50L);
        when(replyRepository.findActiveReplyIdsAroundCursor(5L, 52L, 3, 0)).thenReturn(List.of(
                replyContextCandidate(53L, 0), replyContextCandidate(54L, 0), replyContextCandidate(55L, 0)
        ));
        when(replyRepository.findActiveRepliesWithDetailsByAnnouncementIdAndIdIn(eq(5L), anyCollection()))
                .thenReturn(List.of(replyWithId(54L), anchor, replyWithId(53L)));

        ReplyContextResponse response = replyService.getReplyContext(1L, 5L, 50L, 52L, "newer", 2, "desc", author);

        assertEquals(List.of(54L, 53L), response.content().stream().map(AnnouncementReplyResponse::id).toList());
        assertEquals(54L, response.newerCursor());
        assertTrue(response.hasNewer());
        assertNull(response.olderCursor());
    }

    @Test
    void getReplyContext_LimitOne_ExposesAnchorCursorsForBothDirections() {
        AnnouncementReply anchor = replyWithId(50L);
        when(replyRepository.findActiveReplyIdsAroundCursor(5L, 50L, 1, 1)).thenReturn(List.of(
                replyContextCandidate(51L, 0), replyContextCandidate(49L, 1)
        ));
        when(replyRepository.findActiveRepliesWithDetailsByAnnouncementIdAndIdIn(eq(5L), anyCollection()))
                .thenReturn(List.of(anchor));

        ReplyContextResponse response = replyService.getReplyContext(1L, 5L, 50L, null, null, 1, "desc", author);

        assertEquals(List.of(50L), response.content().stream().map(AnnouncementReplyResponse::id).toList());
        assertEquals(50L, response.newerCursor());
        assertEquals(50L, response.olderCursor());
        assertTrue(response.hasNewer());
        assertTrue(response.hasOlder());
    }

    @Test
    void getReplyContext_AnchorUnavailableDuringHydration_ThrowsException() {
        when(replyRepository.findActiveReplyIdsAroundCursor(5L, 50L, 3, 3)).thenReturn(List.of());
        when(replyRepository.findActiveRepliesWithDetailsByAnnouncementIdAndIdIn(eq(5L), anyCollection()))
                .thenReturn(List.of());

        AppException exception = assertThrows(AppException.class, () ->
                replyService.getReplyContext(1L, 5L, 50L, null, null, 5, "desc", author)
        );

        assertEquals(ErrorCode.REPLY_NOT_FOUND, exception.getErrorCode());
    }

    private AnnouncementReply replyWithId(Long id) {
        AnnouncementReply result = new AnnouncementReply();
        result.setId(id);
        result.setAnnouncement(announcement);
        result.setUser(author);
        result.setContent("Content " + id);
        return result;
    }

    private AnnouncementReplyRepository.ReplyContextCandidate replyContextCandidate(Long id, Integer side) {
        return new AnnouncementReplyRepository.ReplyContextCandidate() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Integer getSide() {
                return side;
            }
        };
    }
}
