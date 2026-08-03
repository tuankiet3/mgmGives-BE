package com.mgmtp.gives.dto.campaign_meeting;

import com.mgmtp.gives.enums.CampaignMemberRole;

public record CampaignMeetingRecipientResponse(
        Long userId,
        String fullName,
        String email,
        CampaignMemberRole roleInCampaign
) {
}
