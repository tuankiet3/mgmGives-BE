package com.mgmtp.gives.dto.campaign_member;

import com.mgmtp.gives.dto.category.CategoryResponse;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record JoinedCampaignResponse(
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
        String role,
        LocalDateTime joinedAt,
        String coverImageUrl,
        List<CategoryResponse> categories,
        boolean hasPendingUnjoinRequest
) {
}
