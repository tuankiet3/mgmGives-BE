package com.mgmtp.gives.dto.announcement;

import java.time.LocalDateTime;
import java.util.List;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;

public record AnnouncementResponse(
        Long id,
        Long campaignId,
        String title,
        String content,
        UserSummary createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CampaignMediaResponse> media
) {
    public record UserSummary(
            Long id,
            String name,
            String email
    ) {
    }
}
