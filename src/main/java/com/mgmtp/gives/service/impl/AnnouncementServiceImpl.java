package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.announcement.AnnouncementResponse;
import com.mgmtp.gives.dto.announcement.AudienceFilter;
import com.mgmtp.gives.dto.announcement.CreateAnnouncementRequest;
import com.mgmtp.gives.dto.announcement.UpdateAnnouncementRequest;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.mapper.CampaignMediaMapper;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.AnnouncementLikeRepository;
import com.mgmtp.gives.entity.AnnouncementLikeId;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.AnnouncementService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.util.HtmlSanitizerUtil;
import org.jsoup.Jsoup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final DonationRepository donationRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMediaMapper campaignMediaMapper;
    private final MediaService mediaService;
    private final NotificationService notificationService;
    private final Executor notificationExecutor;
    private final AnnouncementLikeRepository announcementLikeRepository;

    public AnnouncementServiceImpl(
            AnnouncementRepository announcementRepository,
            CampaignRepository campaignRepository,
            CampaignMemberRepository campaignMemberRepository,
            CampaignFollowerRepository campaignFollowerRepository,
            DonationRepository donationRepository,
            CampaignMediaRepository campaignMediaRepository,
            CampaignMediaMapper campaignMediaMapper,
            MediaService mediaService,
            NotificationService notificationService,
            @Qualifier("notificationExecutor") Executor notificationExecutor,
            AnnouncementLikeRepository announcementLikeRepository) {
        this.announcementRepository = announcementRepository;
        this.campaignRepository = campaignRepository;
        this.campaignMemberRepository = campaignMemberRepository;
        this.campaignFollowerRepository = campaignFollowerRepository;
        this.donationRepository = donationRepository;
        this.campaignMediaRepository = campaignMediaRepository;
        this.campaignMediaMapper = campaignMediaMapper;
        this.mediaService = mediaService;
        this.notificationService = notificationService;
        this.notificationExecutor = notificationExecutor;
        this.announcementLikeRepository = announcementLikeRepository;
    }

    @Override
    @Transactional
    public AnnouncementResponse createAnnouncement(Long campaignId, CreateAnnouncementRequest request, User currentUser) {
        Campaign campaign = findCampaign(campaignId);
        validateCampaignOwnership(campaign, currentUser);

        Announcement announcement = Announcement.builder()
                .campaign(campaign)
                .title(sanitizeContent(request.title()))
                .content(sanitizeContent(request.content()))
                .createdBy(currentUser)
                .publishedAt(LocalDateTime.now())
                .build();

        Announcement savedAnnouncement = announcementRepository.save(announcement);
        saveAnnouncementMedia(savedAnnouncement, request.mediaIds(), campaignId);
        registerNotificationAfterCommit(savedAnnouncement, request.audienceFilter());

        log.info("Announcement created: campaignId={}, announcementId={}, userId={}",
                campaignId, savedAnnouncement.getId(), currentUser.getId());
        return toResponse(savedAnnouncement);
    }

    @Override
    @Transactional
    public AnnouncementResponse updateAnnouncement(Long campaignId, Long announcementId, UpdateAnnouncementRequest request, User currentUser) {
        Campaign campaign = findCampaign(campaignId);
        validateCampaignOwnership(campaign, currentUser);

        Announcement announcement = findAnnouncementForCampaign(campaignId, announcementId);
        announcement.setTitle(sanitizeContent(request.title()));
        announcement.setContent(sanitizeContent(request.content()));

        Announcement savedAnnouncement = announcementRepository.save(announcement);
        saveAnnouncementMedia(savedAnnouncement, request.mediaIds(), campaignId);

        log.info("Announcement updated: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, currentUser.getId());
        return toResponse(savedAnnouncement);
    }

    @Override
    @Transactional
    public void deleteAnnouncement(Long campaignId, Long announcementId, User currentUser) {
        Campaign campaign = findCampaign(campaignId);
        validateCampaignOwnership(campaign, currentUser);
        Announcement announcement = findAnnouncementForCampaign(campaignId, announcementId);

        List<CampaignMedia> associatedMedia = campaignMediaRepository.findByAnnouncementIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(announcementId);
        for (CampaignMedia media : associatedMedia) {
            media.setAnnouncement(null);
            media.setDisplayOrder(null);
        }
        campaignMediaRepository.saveAll(associatedMedia);

        announcementRepository.delete(announcement);
        log.info("Announcement deleted: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, currentUser.getId());
    }

    private void saveAnnouncementMedia(Announcement announcement, List<Long> mediaIds, Long campaignId) {
        List<CampaignMedia> currentMedia = campaignMediaRepository
                .findByAnnouncementIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(announcement.getId());

        mediaService.reconcileMediaTags(campaignId, currentMedia, mediaIds,
                media -> {
                    media.setAnnouncement(null);
                    media.setDisplayOrder(null);
                },
                (media, index) -> {
                    if (media.getContext() == MediaContext.FINAL_REPORT || media.getMeeting() != null) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Media with ID " + media.getId() + " is already attached to another feature");
                    }
                    media.setAnnouncement(announcement);
                    media.setDisplayOrder(index);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnnouncementResponse> getAnnouncementsByCampaign(Long campaignId, Pageable pageable) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        Page<Announcement> announcementPage = announcementRepository.findByCampaignId(campaignId, pageable);
        List<Long> announcementIds = announcementPage.getContent().stream()
                .map(Announcement::getId)
                .toList();

        Map<Long, List<CampaignMedia>> mediaMap;
        if (!announcementIds.isEmpty()) {
            List<CampaignMedia> allMedias = campaignMediaRepository.findByAnnouncementIdInAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(announcementIds);
            mediaMap = allMedias.stream().collect(Collectors.groupingBy(m -> m.getAnnouncement().getId()));
        } else {
            mediaMap = Map.of();
        }

        // Bulk-checks liked announcement IDs for the current user in a single database call, preventing N+1 SELECT loops
        Set<Long> likedAnnouncementIds = Collections.emptySet();
        User currentUser = getCurrentUser();
        if (currentUser != null && !announcementIds.isEmpty()) {
            likedAnnouncementIds = announcementLikeRepository.findLikedAnnouncementIdsByAnnouncementIdInAndUserId(
                    announcementIds, currentUser.getId()
            );
        }

        final Set<Long> finalLikedIds = likedAnnouncementIds;
        return announcementPage.map(announcement -> {
            List<CampaignMedia> mediaList = mediaMap.getOrDefault(announcement.getId(), List.of());
            boolean isLiked = finalLikedIds.contains(announcement.getId());
            return toResponse(announcement, mediaList, isLiked);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementResponse getAnnouncementById(Long campaignId, Long announcementId) {
        return toResponse(findAnnouncementForCampaign(campaignId, announcementId));
    }

    @Override
    public void validateCampaignOwnership(Campaign campaign, User user) {
        if (user == null || campaign.getUser() == null || !Objects.equals(campaign.getUser().getId(), user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ANNOUNCEMENT_ACCESS);
        }
    }

    @Override
    public String sanitizeContent(String content) {
        return HtmlSanitizerUtil.sanitize(content);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<NotificationRecipient> resolveAudience(Campaign campaign, AudienceFilter filter, User publisher) {
        AudienceFilter effectiveFilter = filter == null ? new AudienceFilter(true, true, true) : filter;
        boolean includeAll = effectiveFilter.shouldIncludeAll();
        Map<Long, NotificationRecipient> recipients = new LinkedHashMap<>();

        if (includeAll || Boolean.TRUE.equals(effectiveFilter.includeMembers())) {
            campaignMemberRepository.findMemberRecipientsByCampaignId(campaign.getId())
                    .forEach(recipient -> recipients.putIfAbsent(recipient.userId(), recipient));
        }

        if (includeAll || Boolean.TRUE.equals(effectiveFilter.includeFollowers())) {
            campaignFollowerRepository.findFollowerRecipientsByCampaignId(campaign.getId())
                    .forEach(recipient -> recipients.putIfAbsent(recipient.userId(), recipient));
        }

        if (includeAll || Boolean.TRUE.equals(effectiveFilter.includeDonors())) {
            donationRepository.findDonorRecipientsByCampaignId(campaign.getId())
                    .forEach(recipient -> recipients.putIfAbsent(recipient.userId(), recipient));
        }

        if (publisher != null) {
            recipients.remove(publisher.getId());
        }

        log.info("Resolved announcement audience: campaignId={}, recipientCount={}",
                campaign.getId(), recipients.size());
        return new LinkedHashSet<>(recipients.values());
    }

    private Campaign findCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private Announcement findAnnouncementForCampaign(Long campaignId, Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AppException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        if (announcement.getCampaign() == null || !Objects.equals(announcement.getCampaign().getId(), campaignId)) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return announcement;
    }

    private void registerNotificationAfterCommit(Announcement announcement, AudienceFilter filter) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationExecutor.execute(() -> {
                    try {
                        sendAnnouncementNotifications(announcement, filter);
                    } catch (Exception ex) {
                        log.error("Failed to send announcement notifications: announcementId={}, error={}",
                                announcement.getId(), ex.getMessage(), ex);
                    }
                });
            }
        });
    }

    private String getPlainTextSnippet(String html) {
        if (html == null) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        if (text.length() > 120) {
            return text.substring(0, 117) + "...";
        }
        return text;
    }

    private void sendAnnouncementNotifications(Announcement announcement, AudienceFilter filter) {
        Set<NotificationRecipient> recipients = resolveAudience(announcement.getCampaign(), filter, announcement.getCreatedBy());
        String snippet = getPlainTextSnippet(announcement.getContent());
        String finalMessage = announcement.getTitle();
        if (!snippet.isEmpty()) {
            finalMessage = announcement.getTitle() + " - " + snippet;
        }

        notificationService.createNotification(CreateNotificationCommand.builder()
                .recipients(recipients)
                .type(NotificationType.CAMPAIGN_ANNOUNCEMENT)
                .title("New announcement in \"" + announcement.getCampaign().getTitle() + "\"")
                .message(finalMessage)
                .linkUrl("/campaigns/" + announcement.getCampaign().getId() + "/announcements/" + announcement.getId())
                .build());
    }

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                return userDetails.getUser();
            }
        } catch (Exception ex) {
            log.warn("Failed to get current user from SecurityContextHolder: {}", ex.getMessage());
        }
        return null;
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        List<CampaignMedia> mediaList = campaignMediaRepository.findByAnnouncementIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(announcement.getId());
        
        // Single entity fetch checks liked status individually using existsById
        boolean isLiked = false;
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            isLiked = announcementLikeRepository.existsById(
                    new AnnouncementLikeId(announcement.getId(), currentUser.getId())
            );
        }
        return toResponse(announcement, mediaList, isLiked);
    }

    private AnnouncementResponse toResponse(Announcement announcement, List<CampaignMedia> mediaList, boolean isLiked) {
        User createdBy = announcement.getCreatedBy();

        List<CampaignMediaResponse> mediaResponses = campaignMediaMapper.toResponseList(mediaList);

        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getCampaign() != null ? announcement.getCampaign().getId() : null,
                announcement.getTitle(),
                announcement.getContent(),
                createdBy == null ? null : new AnnouncementResponse.UserSummary(
                        createdBy.getId(),
                        createdBy.getFullName(),
                        createdBy.getEmail(),
                        createdBy.getAvatarUrl()),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                mediaResponses,
                announcement.getLikesCount(),
                announcement.getRepliesCount(),
                isLiked
        );
    }
}
