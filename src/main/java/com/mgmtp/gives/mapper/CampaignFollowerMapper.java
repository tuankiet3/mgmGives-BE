package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign_follower.FollowedCampaignResponse;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.DonationRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public abstract class CampaignFollowerMapper {

    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "title", source = "campaign.title")
    @Mapping(target = "description", source = "campaign.description")
    @Mapping(target = "status", source = "campaign.status")
    @Mapping(target = "startDate", source = "campaign.startDate")
    @Mapping(target = "endDate", source = "campaign.endDate")
    @Mapping(target = "target", source = "campaign.target")
    @Mapping(target = "priority", source = "campaign.priority")
    @Mapping(target = "followedAt", source = "followedAt")
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "categories", source = "campaign.categories")
    @Mapping(target = "currentRaised", ignore = true)
    @Mapping(target = "donorsCount", ignore = true)
    @Mapping(target = "volunteersCount", ignore = true)
    public abstract FollowedCampaignResponse toResponse(CampaignFollower campaignFollower, 
            @org.mapstruct.Context com.mgmtp.gives.dto.campaign_follower.CampaignAggregatesContext context);

    @AfterMapping
    void enrichResponse(CampaignFollower source,
            @MappingTarget FollowedCampaignResponse.FollowedCampaignResponseBuilder builder,
            @org.mapstruct.Context com.mgmtp.gives.dto.campaign_follower.CampaignAggregatesContext context) {
        
        if (source.getCampaign() == null) {
            return;
        }
        Long campaignId = source.getCampaign().getId();

        // Cover image
        if (source.getCampaign().getMedias() != null) {
            String coverUrl = source.getCampaign().getMedias().stream()
                    .filter(CampaignMedia::isCover)
                    .filter(m -> m.getDeletedAt() == null)
                    .map(CampaignMedia::getUrl)
                    .findFirst()
                    .orElse(null);
            builder.coverImageUrl(coverUrl);
        }

        if (context != null) {
            java.util.Map<Long, Long> amountsMap = context.getAmountsMap();
            java.util.Map<Long, Long> donorsMap = context.getDonorsMap();
            java.util.Map<Long, Long> volunteersMap = context.getVolunteersMap();

            // Donation statistics (using batch maps to avoid N+1 queries)
            builder.currentRaised(amountsMap != null ? amountsMap.getOrDefault(campaignId, 0L) : 0L);
            builder.donorsCount(donorsMap != null ? donorsMap.getOrDefault(campaignId, 0L) : 0L);
            builder.volunteersCount(volunteersMap != null ? volunteersMap.getOrDefault(campaignId, 0L) : 0L);
        }
    }
}
