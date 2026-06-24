package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_follower.FollowedCampaignResponse;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.CampaignFollowerMapper;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignRepository;
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
    public PageResponse<FollowedCampaignResponse> getFollowedCampaigns(Long userId, Pageable pageable) {
        Page<CampaignFollower> campaignFollowersPages = campaignFollowerRepo.findAllByUserIdWithCampaign(userId, pageable);

        Page<FollowedCampaignResponse> response = campaignFollowersPages.map(mapper::toResponse);

        return PageResponse.of(response, response.getContent());
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
