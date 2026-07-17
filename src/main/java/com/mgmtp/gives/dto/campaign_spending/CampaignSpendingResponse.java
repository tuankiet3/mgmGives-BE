package com.mgmtp.gives.dto.campaign_spending;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CampaignSpendingResponse(
        Long id,
        Long campaignId,
        Long amount,
        String description,
        LocalDate spentAt,
        UserSummary createdBy,
        List<CampaignMediaResponse> photos,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record UserSummary(
            Long id,
            String fullName,
            String email,
            String avatarUrl
    ) {
    }
}
