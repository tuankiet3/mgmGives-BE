package com.mgmtp.gives.service;

import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberFilterCriteria;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.entity.User;
import org.springframework.data.domain.Pageable;

public interface CampaignMemberService {
    CampaignMemberResponse joinCampaign(User user, Long campaignId);

    void unjoinCampaign(User user, Long campaignId);

    /**
     * @return true when the user is a system ADMIN or a CAMPAIGN_ADMIN member of the campaign
     */
    boolean canManageCampaign(Long campaignId, User user);

    PageResponse<JoinedCampaignResponse> getJoinedCampaigns(Long userId, CampaignMemberFilterCriteria criteria, Pageable pageable);
}