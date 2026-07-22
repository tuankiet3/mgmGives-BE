package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_follower.FollowedCampaignResponse;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CampaignFollowerMapper;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.CampaignFollowerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service @RequiredArgsConstructor @Slf4j
public class CampaignFollowerServiceImpl implements CampaignFollowerService {
    private final CampaignFollowerRepository campaignFollowerRepo;
    private final CampaignFollowerMapper mapper;
    private final CampaignRepository campaignRepo;
    private final DonationRepository donationRepository;
    private final CampaignMemberRepository campaignMemberRepository;

    @Transactional @Override
    public void autoFollow(Long userId, Long campaignId) {
        followIfNotExists(userId, campaignId);
    }

    @Transactional @Override
    public void manualFollow(Long userId, Long campaignId) {
        if (!campaignRepo.existsById(campaignId)) {
            log.warn("Campaign not found: campaignId={}", campaignId);
            throw new AppException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        followIfNotExists(userId, campaignId);
    }

    @Transactional @Override
    public void unfollow(Long userId, Long campaignId) {
        long deleted = campaignFollowerRepo.deleteByCampaignIdAndUserId(campaignId, userId);

        if (deleted > 0) {
            log.info("Campaign unfollowed: userId={}, campaignId={}", userId, campaignId);
        } else {
            log.debug("Unfollow skipped because follow record does not exist: userId={}, campaignId={}", userId, campaignId);
        }
    }

    @Transactional(readOnly = true) @Override
    public PageResponse<FollowedCampaignResponse> getFollowedCampaigns(
            Long userId, com.mgmtp.gives.dto.campaign_follower.CampaignFollowerFilterCriteria criteria, Pageable pageable) {

        String keyword = criteria.getKeyword();
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : "";

        CampaignStatus status = parseEnumFilter(criteria.getStatus(), CampaignStatus.class);
        CampaignPriority priority = parseEnumFilter(criteria.getPriority(), CampaignPriority.class);
        java.util.List<Long> categoryIds = criteria.getCategoryIds();

        boolean hasCategories = categoryIds != null && !categoryIds.isEmpty();
        java.util.List<Long> sanitizedCategoryIds = hasCategories
                ? categoryIds.stream().distinct().toList()
                : java.util.List.of(-1L);
        long categoryCount = hasCategories ? sanitizedCategoryIds.size() : 0L;

        Page<CampaignFollower> page = campaignFollowerRepo.findAllByUserIdWithFilters(
                userId, normalizedKeyword, status, priority, hasCategories, sanitizedCategoryIds, categoryCount, pageable);

        java.util.List<Long> campaignIds = page.getContent().stream()
                .map(cf -> cf.getCampaign().getId())
                .toList();

        java.util.Map<Long, Long> amountMap = new java.util.HashMap<>();
        java.util.Map<Long, Long> donorMap = new java.util.HashMap<>();
        java.util.Map<Long, Long> volunteerMap = new java.util.HashMap<>();

        if (!campaignIds.isEmpty()) {
            java.util.List<Object[]> amounts = donationRepository.sumConfirmedAmountByCampaignIds(campaignIds);
            for (Object[] obj : amounts) {
                amountMap.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            }

            java.util.List<Object[]> donors = donationRepository.countDistinctDonorsByCampaignIds(campaignIds);
            for (Object[] obj : donors) {
                donorMap.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            }

            java.util.List<Object[]> volunteers = campaignMemberRepository.countByCampaignIdsAndRoleInCampaign(
                    campaignIds, com.mgmtp.gives.enums.CampaignMemberRole.VOLUNTEER);
            for (Object[] obj : volunteers) {
                volunteerMap.put(((Number) obj[0]).longValue(), ((Number) obj[1]).longValue());
            }
        }

        com.mgmtp.gives.dto.campaign_follower.CampaignAggregatesContext context = 
                new com.mgmtp.gives.dto.campaign_follower.CampaignAggregatesContext(amountMap, donorMap, volunteerMap);

        Page<FollowedCampaignResponse> response = page.map(cf -> mapper.toResponse(cf, context));
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

    private void followIfNotExists(Long userId, Long campaignId) {
        int inserted = campaignFollowerRepo.insertIgnore(campaignId, userId);

        if (inserted == 1) {
            log.info("Campaign followed: userId={}, campaignId={}", userId, campaignId);
        } else {
            log.debug("Follow skipped because already followed: userId={}, campaignId={}", userId, campaignId);
        }
    }
}
