package com.mgmtp.gives.dto.campaign_member;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record JoinedCampaignResponse(
        Long campaignId,
        String title,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long target,
        String priority,
        String role,
        LocalDateTime joinedAt
) {
}
