package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.announcement.AnnouncementResponse;
import com.mgmtp.gives.dto.announcement.AudienceFilter;
import com.mgmtp.gives.dto.announcement.CreateAnnouncementRequest;
import com.mgmtp.gives.dto.announcement.UpdateAnnouncementRequest;
import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.AnnouncementStatus;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.AnnouncementService;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.util.HtmlSanitizerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final DonationRepository donationRepository;
    private final NotificationService notificationService;
    private final Executor notificationExecutor;

    public AnnouncementServiceImpl(
            AnnouncementRepository announcementRepository,
            CampaignRepository campaignRepository,
            CampaignMemberRepository campaignMemberRepository,
            CampaignFollowerRepository campaignFollowerRepository,
            DonationRepository donationRepository,
            NotificationService notificationService,
            @Qualifier("notificationExecutor") Executor notificationExecutor) {
        this.announcementRepository = announcementRepository;
        this.campaignRepository = campaignRepository;
        this.campaignMemberRepository = campaignMemberRepository;
        this.campaignFollowerRepository = campaignFollowerRepository;
        this.donationRepository = donationRepository;
        this.notificationService = notificationService;
        this.notificationExecutor = notificationExecutor;
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
                .status(AnnouncementStatus.PUBLISHED)
                .publishedAt(LocalDateTime.now())
                .build();

        Announcement savedAnnouncement = announcementRepository.save(announcement);
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
        announcementRepository.delete(announcement);
        log.info("Announcement deleted: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AnnouncementResponse> getAnnouncementsByCampaign(Long campaignId, Pageable pageable) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        return announcementRepository.findByCampaignId(campaignId, pageable).map(this::toResponse);
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
    public Set<NotificationRecipient> resolveAudience(Campaign campaign, AudienceFilter filter) {
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

    private void sendAnnouncementNotifications(Announcement announcement, AudienceFilter filter) {
        Set<NotificationRecipient> recipients = resolveAudience(announcement.getCampaign(), filter);
        notificationService.createNotification(CreateNotificationCommand.builder()
                .recipients(recipients)
                .type(NotificationType.CAMPAIGN_ANNOUNCEMENT)
                .title("New Announcement: " + announcement.getTitle())
                .message(announcement.getContent())
                .linkUrl("/campaigns/" + announcement.getCampaign().getId() + "/announcements/" + announcement.getId())
                .build());
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        User createdBy = announcement.getCreatedBy();
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getCampaign() != null ? announcement.getCampaign().getId() : null,
                announcement.getTitle(),
                announcement.getContent(),
                createdBy == null ? null : new AnnouncementResponse.UserSummary(
                        createdBy.getId(),
                        createdBy.getFullName(),
                        createdBy.getEmail()),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt()
        );
    }
}
