package com.mgmtp.gives.service;

import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_follower.FollowedCampaignResponse;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import org.springframework.data.domain.Pageable;

public interface CampaignFollowerService {
    void autoFollow(Long userId, Long campaignId);

    void manualFollow(Long userId, Long campaignId);

    void unfollow(Long userId, Long campaignId);

    PageResponse<FollowedCampaignResponse> getFollowedCampaigns(Long userId, com.mgmtp.gives.dto.campaign_follower.CampaignFollowerFilterCriteria criteria, Pageable pageable);
}
