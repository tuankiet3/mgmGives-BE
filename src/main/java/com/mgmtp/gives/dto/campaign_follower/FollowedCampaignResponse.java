package com.mgmtp.gives.dto.campaign_follower;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record FollowedCampaignResponse(
        Long campaignId,
        String title,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long target,
        String priority,
        LocalDateTime followedAt
) {

}
