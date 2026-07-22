package com.mgmtp.gives.service;

import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberFilterCriteria;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.dto.campaign_member.UnjoinCampaignResponse;
import com.mgmtp.gives.dto.campaign_member.UnjoinRequestResponse;
import com.mgmtp.gives.entity.User;
import org.springframework.data.domain.Pageable;

public interface CampaignMemberService {
    CampaignMemberResponse joinCampaign(User user, Long campaignId);

    /**
     * Unjoins immediately, unless the user has a non-DONE assigned task in the campaign,
     * in which case an unjoin request is created pending campaign admin approval.
     */
    UnjoinCampaignResponse unjoinCampaign(User user, Long campaignId);

    void cancelUnjoinRequest(User user, Long campaignId);

    PageResponse<UnjoinRequestResponse> getUnjoinRequests(Long campaignId, User admin, Pageable pageable);

    void approveUnjoinRequest(Long campaignId, Long targetUserId, User admin);

    void rejectUnjoinRequest(Long campaignId, Long targetUserId, String reason, User admin);

    /**
     * @return true when the user is a system ADMIN or a CAMPAIGN_ADMIN member of the campaign
     */
    boolean canManageCampaign(Long campaignId, User user);

    PageResponse<JoinedCampaignResponse> getJoinedCampaigns(Long userId, CampaignMemberFilterCriteria criteria, Pageable pageable);
}