package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.dto.category.CategoryResponse;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PublicCampaignResponse(
        Long id,
        String title,
        String description,
        CampaignStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Long target,
        Long currentRaised,
        CampaignPriority priority,
        Boolean acceptsMoney,
        Boolean acceptsGoods,
        String creatorName,
        String creatorAvatarUrl,
        List<CategoryResponse> categories,
        List<CampaignMediaResponse> medias,
        List<CampaignMediaResponse> media,
        String coverImageUrl,
        Long volunteersCount,
        Long donorsCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
