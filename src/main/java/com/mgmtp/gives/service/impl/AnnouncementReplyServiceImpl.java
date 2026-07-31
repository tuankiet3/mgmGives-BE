package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.announcement.*;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.AnnouncementReply;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.event.notification.AnnouncementReplyCreatedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyUpdatedEvent;
import com.mgmtp.gives.event.notification.AnnouncementReplyDeletedEvent;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.AnnouncementReplyMapper;
import com.mgmtp.gives.repository.AnnouncementReplyRepository;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.security.AnnouncementAccessAuthorizer;
import com.mgmtp.gives.service.AnnouncementReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementReplyServiceImpl implements AnnouncementReplyService {

    private final AnnouncementReplyRepository replyRepository;
    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final AnnouncementReplyMapper replyMapper;
    private final AnnouncementAccessAuthorizer announcementAccessAuthorizer;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AnnouncementReplyResponse createReply(Long campaignId, Long announcementId, CreateReplyRequest request, User currentUser) {
        log.info("Creating reply: campaignId={}, announcementId={}, userId={}", campaignId, announcementId, currentUser.getId());

        Announcement announcement = announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);
        // Authentication supplies a detached user; reload it so the reply is persisted with a managed association.
        User managedCurrentUser = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        AnnouncementReply inReplyTo = request.inReplyToReplyId() == null
                ? null
                : requireActiveReplyWithUserInAnnouncement(request.inReplyToReplyId(), announcementId);

        AnnouncementReply reply = AnnouncementReply.builder()
                .announcement(announcement)
                .user(managedCurrentUser)
                .inReplyTo(inReplyTo)
                .content(request.content())
                .isEdited(false)
                .build();

        AnnouncementReply savedReply = replyRepository.save(reply);
        announcementRepository.incrementRepliesCount(announcement.getId());
        eventPublisher.publishEvent(new AnnouncementReplyCreatedEvent(
                campaignId,
                announcementId,
                announcement.getTitle(),
                savedReply.getId(),
                managedCurrentUser.getId(),
                displayName(managedCurrentUser),
                announcement.getCreatedBy() == null ? null : announcement.getCreatedBy().getId(),
                inReplyTo == null || inReplyTo.getUser() == null ? null : inReplyTo.getUser().getId()
        ));

        AnnouncementReplyResponse response = replyMapper.toResponse(savedReply);

        log.info("Reply created successfully: replyId={}, userId={}", savedReply.getId(), currentUser.getId());
        return response;
    }

    private static String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail();
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
            eventPublisher.publishEvent(new AnnouncementReplyUpdatedEvent(campaignId, announcementId, reply.getId(), response));
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
        eventPublisher.publishEvent(new AnnouncementReplyDeletedEvent(campaignId, announcementId, replyId));
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

        announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);
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

    @Override
    @Transactional(readOnly = true)
    public ReplyContextResponse getReplyContext(
            Long campaignId,
            Long announcementId,
            Long replyId,
            Long cursor,
            String direction,
            int limit,
            String sort,
            User currentUser
    ) {
        announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);
        if (!"asc".equalsIgnoreCase(sort) && !"desc".equalsIgnoreCase(sort)) {
            throw new AppException(ErrorCode.INVALID_ENUM_VALUE);
        }
        if (direction != null && !"newer".equalsIgnoreCase(direction) && !"older".equalsIgnoreCase(direction)) {
            throw new AppException(ErrorCode.INVALID_ENUM_VALUE);
        }

        if (direction == null) {
            return getInitialReplyContext(announcementId, replyId, limit, sort);
        }
        if (cursor == null) {
            throw new AppException(ErrorCode.INVALID_ENUM_VALUE);
        }

        boolean loadingNewer = "newer".equalsIgnoreCase(direction);
        ReplyIdsBySide candidates = findReplyIdsAroundCursor(
                announcementId,
                cursor,
                loadingNewer ? limit + 1 : 0,
                loadingNewer ? 0 : limit + 1
        );
        List<Long> candidateIds = loadingNewer ? candidates.newerIds() : candidates.olderIds();
        boolean hasMore = candidateIds.size() > limit;
        List<Long> pageIds = take(candidateIds, limit);
        Map<Long, AnnouncementReply> repliesById = findActiveRepliesWithDetails(announcementId, replyId, pageIds);
        requireAnchor(repliesById, replyId);
        List<AnnouncementReplyResponse> content = mapIdsInSortOrder(pageIds, repliesById, sort, loadingNewer);
        Long nextCursor = pageIds.isEmpty() ? null : pageIds.getLast();

        return new ReplyContextResponse(
                content,
                replyId,
                loadingNewer ? nextCursor : null,
                loadingNewer ? null : nextCursor,
                loadingNewer && hasMore,
                !loadingNewer && hasMore
        );
    }

    private ReplyContextResponse getInitialReplyContext(
            Long announcementId,
            Long anchorReplyId,
            int limit,
            String sort
    ) {
        // Reserve the anchor, then split the remaining initial window around it.
        int newerLimit = (limit - 1) / 2;
        int olderLimit = limit - 1 - newerLimit;
        ReplyIdsBySide candidates = findReplyIdsAroundCursor(
                announcementId,
                anchorReplyId,
                newerLimit + 1,
                olderLimit + 1
        );
        List<Long> newerCandidates = candidates.newerIds();
        List<Long> olderCandidates = candidates.olderIds();

        boolean hasNewer = newerCandidates.size() > newerLimit;
        boolean hasOlder = olderCandidates.size() > olderLimit;
        List<Long> newerIds = take(newerCandidates, newerLimit);
        List<Long> olderIds = take(olderCandidates, olderLimit);
        List<Long> idsToHydrate = new ArrayList<>(newerIds.size() + olderIds.size());
        idsToHydrate.addAll(newerIds);
        idsToHydrate.addAll(olderIds);
        Map<Long, AnnouncementReply> repliesById = findActiveRepliesWithDetails(announcementId, anchorReplyId, idsToHydrate);
        AnnouncementReply anchor = requireAnchor(repliesById, anchorReplyId);
        List<AnnouncementReplyResponse> newerContent = mapIdsInSortOrder(newerIds, repliesById, sort, true);
        List<AnnouncementReplyResponse> olderContent = mapIdsInSortOrder(olderIds, repliesById, sort, false);
        List<AnnouncementReplyResponse> content = new ArrayList<>(newerContent.size() + olderContent.size() + 1);

        if ("asc".equalsIgnoreCase(sort)) {
            content.addAll(olderContent);
            content.add(replyMapper.toResponse(anchor));
            content.addAll(newerContent);
        } else {
            content.addAll(newerContent);
            content.add(replyMapper.toResponse(anchor));
            content.addAll(olderContent);
        }

        return new ReplyContextResponse(
                content,
                anchorReplyId,
                newerIds.isEmpty() ? (hasNewer ? anchorReplyId : null) : newerIds.getLast(),
                olderIds.isEmpty() ? (hasOlder ? anchorReplyId : null) : olderIds.getLast(),
                hasNewer,
                hasOlder
        );
    }

    private ReplyIdsBySide findReplyIdsAroundCursor(
            Long announcementId,
            Long cursor,
            int newerLimit,
            int olderLimit
    ) {
        List<Long> newerIds = new ArrayList<>();
        List<Long> olderIds = new ArrayList<>();
        for (AnnouncementReplyRepository.ReplyContextCandidate reply : replyRepository.findActiveReplyIdsAroundCursor(
                announcementId, cursor, newerLimit, olderLimit)) {
            if (reply.getSide() == 0) {
                newerIds.add(reply.getId());
            } else {
                olderIds.add(reply.getId());
            }
        }
        // UNION ALL does not guarantee the CTE branches' output order.
        newerIds.sort(Comparator.naturalOrder());
        olderIds.sort(Comparator.reverseOrder());
        return new ReplyIdsBySide(newerIds, olderIds);
    }

    private Map<Long, AnnouncementReply> findActiveRepliesWithDetails(
            Long announcementId,
            Long anchorReplyId,
            List<Long> replyIds
    ) {
        Set<Long> idsToHydrate = new LinkedHashSet<>(replyIds);
        // Fetch the anchor with every page so it is validated in the same query as hydrated replies.
        idsToHydrate.add(anchorReplyId);
        return replyRepository.findActiveRepliesWithDetailsByAnnouncementIdAndIdIn(announcementId, idsToHydrate)
                .stream()
                .collect(Collectors.toMap(AnnouncementReply::getId, Function.identity()));
    }

    private AnnouncementReply requireAnchor(Map<Long, AnnouncementReply> repliesById, Long anchorReplyId) {
        AnnouncementReply anchor = repliesById.get(anchorReplyId);
        if (anchor == null) {
            throw new AppException(ErrorCode.REPLY_NOT_FOUND);
        }
        return anchor;
    }

    private List<Long> take(List<Long> replyIds, int limit) {
        return replyIds.size() <= limit ? replyIds : replyIds.subList(0, limit);
    }

    private List<AnnouncementReplyResponse> mapIdsInSortOrder(
            List<Long> replyIds,
            Map<Long, AnnouncementReply> repliesById,
            String sort,
            boolean sourceIsAscending
    ) {
        List<Long> orderedIds = new ArrayList<>(replyIds);
        boolean shouldBeAscending = "asc".equalsIgnoreCase(sort);
        if (sourceIsAscending != shouldBeAscending) {
            Collections.reverse(orderedIds);
        }
        return orderedIds.stream()
                .map(repliesById::get)
                .filter(Objects::nonNull)
                .map(replyMapper::toResponse)
                .toList();
    }

    private AnnouncementReply requireActiveAccessibleReply(
            Long campaignId,
            Long announcementId,
            Long replyId,
            User currentUser
    ) {
        announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);

        return requireActiveReplyInAnnouncement(replyId, announcementId);
    }

    private AnnouncementReply requireActiveReplyInAnnouncement(Long replyId, Long announcementId) {
        return replyRepository.findByIdAndAnnouncementId(replyId, announcementId)
                .filter(reply -> reply.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.REPLY_NOT_FOUND));
    }

    private AnnouncementReply requireActiveReplyWithUserInAnnouncement(Long replyId, Long announcementId) {
        return replyRepository.findByIdAndAnnouncementIdWithUser(replyId, announcementId)
                .filter(reply -> reply.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.REPLY_NOT_FOUND));
    }

    private record ReplyIdsBySide(List<Long> newerIds, List<Long> olderIds) {
    }
}
