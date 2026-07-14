package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_follower.CampaignAggregatesContext;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberFilterCriteria;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CampaignMemberMapper;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.CampaignFollowerService;
import com.mgmtp.gives.service.CampaignMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service @RequiredArgsConstructor @Slf4j
public class CampaignMemberServiceImpl implements CampaignMemberService {

    private final CampaignMemberRepository campaignMemberRepo;
    private final CampaignRepository campaignRepo;
    private final CampaignFollowerService campaignFollowerService;
    private final CampaignMemberMapper campaignMemberMapper;
    private final DonationRepository donationRepository;
    private final CampaignMediaRepository campaignMediaRepository;

    @Transactional @Override
    public CampaignMemberResponse joinCampaign(User user, Long campaignId) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new AppException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS
                && campaign.getStatus() != CampaignStatus.APPROVED) {
            throw new AppException(ErrorCode.CAMPAIGN_NOT_IN_PROGRESS);
        }

        CampaignMember existing = campaignMemberRepo.findByCampaignIdAndUserId(campaignId, user.getId())
                .orElse(null);
        if (existing != null) {
            return new CampaignMemberResponse(
                    existing.getId(),
                    campaignId,
                    user.getId(),
                    existing.getRoleInCampaign().name(),
                    existing.getJoinedAt()
            );
        }

        CampaignMember member = CampaignMember.builder()
                .campaign(campaign)
                .user(user)
                .roleInCampaign(CampaignMemberRole.VOLUNTEER)
                .joinedAt(LocalDateTime.now())
                .build();

        CampaignMember saved;
        try {
            saved = campaignMemberRepo.save(member);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CAMPAIGN_ALREADY_JOINED);
        }

        try {
            campaignFollowerService.autoFollow(user.getId(), campaignId);
        } catch (DataAccessException e) {
            log.warn("Auto-follow failed after join: userId={}, campaignId={}", user.getId(), campaignId, e);
        }

        log.info("User joined campaign: userId={}, campaignId={}", user.getId(), campaignId);

        return new CampaignMemberResponse(
                saved.getId(),
                campaignId,
                user.getId(),
                saved.getRoleInCampaign().name(),
                saved.getJoinedAt()
        );
    }

    @Transactional(readOnly = true) @Override
    public PageResponse<JoinedCampaignResponse> getJoinedCampaigns(
            Long userId, CampaignMemberFilterCriteria criteria, Pageable pageable) {

        String keyword = criteria.getKeyword();
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : "";

        CampaignStatus status = parseEnumFilter(criteria.getStatus(), CampaignStatus.class);
        CampaignPriority priority = parseEnumFilter(criteria.getPriority(), CampaignPriority.class);
        java.util.List<Long> categoryIds = criteria.getCategoryIds();

        boolean hasCategories = categoryIds != null && !categoryIds.isEmpty();
        java.util.List<Long> sanitizedCategoryIds = hasCategories ? categoryIds : java.util.List.of(-1L);
        long categoryCount = hasCategories ? categoryIds.size() : 0L;

        Page<CampaignMember> page = campaignMemberRepo.findAllByUserIdWithFilters(
                userId, CampaignMemberRole.VOLUNTEER, normalizedKeyword, status, priority, hasCategories,
                sanitizedCategoryIds, categoryCount, pageable);

        java.util.List<Long> campaignIds = page.getContent().stream()
                .map(cm -> cm.getCampaign().getId())
                .toList();

        java.util.Map<Long, Long> amountMap = new java.util.HashMap<>();
        java.util.Map<Long, Long> donorMap = new java.util.HashMap<>();
        java.util.Map<Long, Long> volunteerMap = new java.util.HashMap<>();
        java.util.Map<Long, String> coverImageMap = new java.util.HashMap<>();

        if (!campaignIds.isEmpty()) {
            java.util.List<Object[]> amounts = donationRepository.sumConfirmedAmountByCampaignIds(campaignIds);
            for (Object[] obj : amounts) {
                amountMap.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            }

            java.util.List<Object[]> donors = donationRepository.countDistinctDonorsByCampaignIds(campaignIds);
            for (Object[] obj : donors) {
                donorMap.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            }

            java.util.List<Object[]> volunteers = campaignMemberRepo.countByCampaignIdsAndRoleInCampaign(
                    campaignIds, CampaignMemberRole.VOLUNTEER);
            for (Object[] obj : volunteers) {
                volunteerMap.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            }

            // Batch-fetch cover images instead of a collection fetch-join on the paginated
            // query, which would multiply result rows for campaigns with several media rows.
            java.util.List<CampaignMedia> coverImages = campaignMediaRepository.findCoverImagesByCampaignIds(campaignIds);
            for (CampaignMedia media : coverImages) {
                coverImageMap.putIfAbsent(media.getCampaign().getId(), media.getUrl());
            }
        }

        CampaignAggregatesContext context = new CampaignAggregatesContext(amountMap, donorMap, volunteerMap);

        Page<JoinedCampaignResponse> response = page.map(cm -> campaignMemberMapper.toJoinedResponse(cm, context, coverImageMap));
        return PageResponse.of(response, response.getContent());
    }

    private <T extends Enum<T>> T parseEnumFilter(String value, Class<T> enumType) {
        if (value == null || value.equals("ALL")) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_ENUM_VALUE);
        }
    }

    @Transactional @Override
    public void unjoinCampaign(User user, Long campaignId) {
        boolean isAdmin = campaignMemberRepo.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaignId, user.getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
        if (isAdmin) {
            throw new AppException(ErrorCode.CAMPAIGN_ADMIN_CANNOT_LEAVE);
        }

        long deleted = campaignMemberRepo.deleteByCampaignIdAndUserId(campaignId, user.getId());

        if (deleted > 0) {
            log.info("User unjoined campaign: userId={}, campaignId={}", user.getId(), campaignId);
        } else {
            log.debug("Unjoin skipped because member record does not exist: userId={}, campaignId={}", user.getId(), campaignId);
        }
    }

    @Transactional(readOnly = true) @Override
    public boolean canManageCampaign(Long campaignId, User user) {
        if (user == null) {
            return false;
        }
        return campaignMemberRepo.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaignId, user.getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
    }
}