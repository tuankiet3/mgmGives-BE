package com.mgmtp.gives.dto.campaign_follower;

import com.mgmtp.gives.dto.category.CategoryResponse;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record FollowedCampaignResponse(
        Long campaignId,
        String title,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long target,
        Long currentRaised,
        Long donorsCount,
        Long volunteersCount,
        String priority,
        LocalDateTime followedAt,
        String coverImageUrl,
        List<CategoryResponse> categories
) {

}
