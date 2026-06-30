package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.notification.publisher.CampaignNotificationPublisher;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.service.AdminCampaignService;
import com.mgmtp.gives.entity.CampaignMedia;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.mgmtp.gives.specification.CampaignSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCampaignServiceImpl implements AdminCampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignNotificationPublisher publisher;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional(readOnly = true)
    public Page<Campaign> getPendingCampaigns(Pageable pageable) {
        return campaignRepository.findByStatus(CampaignStatus.PENDING, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Campaign> getCampaigns(CampaignStatus status, List<Long> categoryIds, String keyword,
            Pageable pageable) {
        Specification<Campaign> spec = Specification.allOf(
                CampaignSpecifications.hasStatus(status),
                CampaignSpecifications.hasCategories(categoryIds),
                CampaignSpecifications.matchesKeyword(keyword),
                (root, query, cb) -> cb.notEqual(root.get("status"), CampaignStatus.DRAFT));
        return campaignRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Campaign get failed (not found): id={}", id);
                    return new ResourceNotFoundException(
                            ErrorCode.CAMPAIGN_NOT_FOUND,
                            "Campaign not found with ID: " + id);
                });
    }

    @Override
    @Transactional
    public Campaign approveCampaign(Long id, User adminUser) {
        Campaign campaign = getCampaignById(id);

        if (campaign.getStatus() != CampaignStatus.PENDING) {
            log.warn("Approve campaign rejected (invalid status): id={}, currentStatus={}, adminId={}",
                    id, campaign.getStatus(), adminUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_REVIEW,
                    "Only PENDING campaigns can be approved");
        }
        CampaignStatus oldStatus = campaign.getStatus();

        CampaignStatus newStatus = shouldStartImmediately(campaign)
                ? CampaignStatus.IN_PROGRESS
                : CampaignStatus.APPROVED;

        campaign.setStatus(newStatus);

        campaign.setApprovedAt(LocalDateTime.now());
        campaign.setApprovedBy(adminUser);
        campaign.setRejectionReason(null); // Clear any previous rejection reason

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign approved successfully: id={}, adminId={}", id, adminUser.getId());

        // Auto-promote submitter to Campaign Admin
        if (!campaignMemberRepository.existsByCampaignIdAndUserId(
                campaign.getId(), campaign.getUser().getId())) {
            CampaignMember member = CampaignMember.builder()
                    .campaign(campaign)
                    .user(campaign.getUser())
                    .roleInCampaign(CampaignMemberRole.CAMPAIGN_ADMIN)
                    .joinedAt(LocalDateTime.now())
                    .build();
            campaignMemberRepository.save(member);
            log.info("Submitter auto-promoted to Campaign Admin: userId={}, campaignId={}",
                    campaign.getUser().getId(), campaign.getId());
        }

        publisher.publishCampaignStatusChanged(saved, oldStatus, saved.getStatus());
        log.info("Campaign approved successfully: id={}, adminId={}", id, adminUser.getId());

        return saved;
    }

    @Override
    @Transactional
    public Campaign rejectCampaign(Long id, String reason, User adminUser) {
        Campaign campaign = getCampaignById(id);

        if (campaign.getStatus() != CampaignStatus.PENDING) {
            log.warn("Reject campaign rejected (invalid status): id={}, currentStatus={}, adminId={}",
                    id, campaign.getStatus(), adminUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_REVIEW,
                    "Only PENDING campaigns can be rejected");
        }
        CampaignStatus oldStatus = campaign.getStatus();

        campaign.setStatus(CampaignStatus.REJECTED);
        campaign.setRejectionReason(reason);
        campaign.setApprovedBy(adminUser); // Track who rejected
        campaign.setApprovedAt(null); // Not approved

        Campaign saved = campaignRepository.save(campaign);

        publisher.publishCampaignStatusChanged(
                saved,
                oldStatus,
                saved.getStatus());

        log.info("Campaign rejected successfully: id={}, adminId={}, reason='{}'", id, adminUser.getId(), reason);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMedia> getActiveMediasByCampaignId(Long campaignId) {
        return campaignMediaRepository.findByCampaignIdAndDeletedAtIsNull(campaignId);
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id, User adminUser) {
        Campaign campaign = getCampaignById(id);

        if (campaign.getStatus() != CampaignStatus.PENDING
                && campaign.getStatus() != CampaignStatus.REJECTED
                && campaign.getStatus() != CampaignStatus.DRAFT) {
            log.warn("Admin delete campaign denied: invalid status. campaignId={}, status={}, adminId={}",
                    id, campaign.getStatus(), adminUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_DELETE);
        }

        // Delete physical files on disk
        if (campaign.getMedias() != null) {
            for (CampaignMedia media : campaign.getMedias()) {
                if (media.getUrl() != null) {
                    Path path = Paths.get(uploadDir).resolve(media.getUrl());
                    try {
                        Files.deleteIfExists(path);
                        log.info("Deleted physical media file: {}", media.getUrl());
                    } catch (IOException e) {
                        log.warn("Failed to delete physical file: {}", media.getUrl(), e);
                    }
                }
            }
        }

        campaignRepository.delete(campaign);
        log.info("Admin deleted campaign successfully: id={}, title={}, adminId={}",
                id, campaign.getTitle(), adminUser.getId());
    }

    private boolean shouldStartImmediately(Campaign campaign) {
        return campaign.getStartDate() != null
                && !campaign.getStartDate().isAfter(LocalDateTime.now());
    }
}
