package com.mgmtp.gives.service;

import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberFilterCriteria;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignRosterResponse;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.dto.campaign_member.UnjoinCampaignResponse;
import com.mgmtp.gives.dto.campaign_member.UnjoinRequestResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.MemberListVisibility;
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

    /**
     * Volunteer roster shaped per viewer: admins and members get the full list; any other
     * signed-in user gets it only when the campaign's roster visibility is PUBLIC, minus
     * members who opted out. An anonymous (not signed in) viewer never gets the list
     * regardless of visibility — always just the aggregate count.
     *
     * @param viewer the requesting user, or null for anonymous visitors
     */
    CampaignRosterResponse getCampaignRoster(Long campaignId, User viewer);

    /** Campaign admin only: sets the campaign-level roster visibility. */
    void updateRosterVisibility(Long campaignId, MemberListVisibility visibility, User admin);

    /** Sets the current user's own hide-from-public-roster flag on their membership. */
    void updateOwnRosterVisibility(Long campaignId, boolean hidden, User user);
}