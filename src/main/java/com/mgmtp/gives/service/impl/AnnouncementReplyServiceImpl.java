package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.announcement.*;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.AnnouncementReply;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.AnnouncementReplyMapper;
import com.mgmtp.gives.repository.AnnouncementReplyRepository;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.security.AnnouncementAccessAuthorizer;
import com.mgmtp.gives.service.AnnouncementReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementReplyServiceImpl implements AnnouncementReplyService {

    private final AnnouncementReplyRepository replyRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReplyMapper replyMapper;
    private final AnnouncementAccessAuthorizer announcementAccessAuthorizer;

    @Override
    @Transactional
    public AnnouncementReplyResponse createReply(Long campaignId, Long announcementId, CreateReplyRequest request, User currentUser) {
        log.info("Creating reply: campaignId={}, announcementId={}, userId={}", campaignId, announcementId, currentUser.getId());

        Announcement announcement = announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);

        AnnouncementReply reply = AnnouncementReply.builder()
                .announcement(announcement)
                .user(currentUser)
                .content(request.content())
                .isEdited(false)
                .version(0L)
                .build();

        AnnouncementReply savedReply = replyRepository.save(reply);
        announcementRepository.incrementRepliesCount(announcement.getId());

        // The currentUser passed from the controller is a detached entity (loaded during
        // JWT filter authentication, outside this transaction). After save(), Hibernate may
        // replace it with a lazy proxy reference. Re-attach the fully-loaded user object
        // so MapStruct can access user.getFullName() without triggering a LazyInitializationException.
        savedReply.setUser(currentUser);

        AnnouncementReplyResponse response = replyMapper.toResponse(savedReply);

        log.info("Reply created successfully: replyId={}, userId={}", savedReply.getId(), currentUser.getId());
        return response;
    }

    @Override
    @Transactional
    public AnnouncementReplyResponse updateReply(Long campaignId, Long announcementId, Long replyId, UpdateReplyRequest request, User currentUser) {
        log.info("Updating reply: campaignId={}, announcementId={}, replyId={}, userId={}", campaignId, announcementId, replyId, currentUser.getId());

        AnnouncementReply reply = requireActiveAccessibleReply(campaignId, announcementId, replyId, currentUser);

        // Authorization check: only author can edit
        if (!reply.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED_REPLY_ACTION);
        }

        // If content is identical, skip the database write.
        String newContent = request.content();
        if (!Objects.equals(reply.getContent(), newContent)) {
            reply.setContent(newContent);
            reply.setEdited(true);
            reply = replyRepository.save(reply);
            log.info("Reply updated: replyId={}", reply.getId());

            AnnouncementReplyResponse response = replyMapper.toResponse(reply);
            return response;
        }

        return replyMapper.toResponse(reply);
    }

    @Override
    @Transactional
    public void deleteReply(Long campaignId, Long announcementId, Long replyId, User currentUser) {
        log.info("Deleting reply: campaignId={}, announcementId={}, replyId={}, userId={}", campaignId, announcementId, replyId, currentUser.getId());

        AnnouncementReply reply = requireActiveAccessibleReply(campaignId, announcementId, replyId, currentUser);

        // Determine deleted_by_role based on ownership priority (AUTHOR > CAMPAIGN_CREATOR > ADMIN)
        String role = getUserRoleForReply(currentUser, reply);

        reply.setDeletedAt(LocalDateTime.now());
        reply.setDeletedBy(currentUser);
        reply.setDeletedByRole(role);
        replyRepository.save(reply);

        announcementRepository.decrementRepliesCount(reply.getAnnouncement().getId());
        log.info("Reply soft-deleted: replyId={}, role={}", reply.getId(), role);
    }

    private static @NonNull String getUserRoleForReply(User currentUser, AnnouncementReply reply) {
        String role;
        if (reply.getUser().getId().equals(currentUser.getId())) {
            role = "AUTHOR";
        } else if (reply.getAnnouncement().getCampaign().getUser() != null &&
                   reply.getAnnouncement().getCampaign().getUser().getId().equals(currentUser.getId())) {
            role = "CAMPAIGN_CREATOR";
        } else if (currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN) {
            role = "ADMIN";
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED_REPLY_ACTION);
        }
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public ReplyPageResponse<AnnouncementReplyResponse> getReplies(Long campaignId, Long announcementId, Long cursor, int limit, String sort, User currentUser) {
        log.info("Fetching replies: campaignId={}, announcementId={}, cursor={}, limit={}, sort={}", campaignId, announcementId, cursor, limit, sort);

        Announcement announcement = announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);
        if (!"asc".equalsIgnoreCase(sort) && !"desc".equalsIgnoreCase(sort)) {
            throw new AppException(ErrorCode.INVALID_ENUM_VALUE);
        }

        List<AnnouncementReply> replies;
        // Request (limit + 1) records. The extra record acts as a lookahead flag
        // to detect if there is a next page of records, avoiding an expensive count query.
        Pageable pageable = PageRequest.of(0, limit + 1);

        if ("asc".equalsIgnoreCase(sort)) {
            if (cursor == null) {
                replies = replyRepository.findRepliesAscendingFirstPage(announcementId, pageable);
            } else {
                replies = replyRepository.findRepliesAscendingWithCursor(announcementId, cursor, pageable);
            }
        } else { // default desc
            if (cursor == null) {
                replies = replyRepository.findRepliesDescendingFirstPage(announcementId, pageable);
            } else {
                replies = replyRepository.findRepliesDescendingWithCursor(announcementId, cursor, pageable);
            }
        }

        // If we retrieved limit + 1 items, then the next page exists.
        boolean hasMore = replies.size() > limit;
        
        // Remove the extra lookahead item from the final returned list.
        List<AnnouncementReply> returnedReplies = hasMore ? replies.subList(0, limit) : replies;

        List<AnnouncementReplyResponse> content = returnedReplies.stream()
                .map(replyMapper::toResponse)
                .toList();

        // The cursor for the next page is the ID of the last item in the returned list.
        Long nextCursor = null;
        if (hasMore && !content.isEmpty()) {
            nextCursor = content.getLast().id();
        }

        return new ReplyPageResponse<>(content, nextCursor);
    }

    private AnnouncementReply requireActiveAccessibleReply(
            Long campaignId,
            Long announcementId,
            Long replyId,
            User currentUser
    ) {
        Announcement announcement = announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);

        return replyRepository.findByIdAndAnnouncementId(replyId, announcementId)
                .filter(reply -> reply.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.REPLY_NOT_FOUND));
    }
}
