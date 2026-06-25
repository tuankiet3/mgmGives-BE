package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.entity.User;

public interface CampaignMemberService {
    CampaignMemberResponse joinCampaign(User user, Long campaignId);

    void unjoinCampaign(User user, Long campaignId);
}