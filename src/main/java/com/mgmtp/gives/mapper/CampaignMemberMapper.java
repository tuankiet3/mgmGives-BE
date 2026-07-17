package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign_follower.CampaignAggregatesContext;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.entity.CampaignMember;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.Map;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public abstract class CampaignMemberMapper {

    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "title", source = "campaign.title")
    @Mapping(target = "description", source = "campaign.description")
    @Mapping(target = "status", source = "campaign.status")
    @Mapping(target = "startDate", source = "campaign.startDate")
    @Mapping(target = "endDate", source = "campaign.endDate")
    @Mapping(target = "target", source = "campaign.target")
    @Mapping(target = "priority", source = "campaign.priority")
    @Mapping(target = "role", source = "roleInCampaign")
    @Mapping(target = "joinedAt", source = "joinedAt")
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "categories", source = "campaign.categories")
    @Mapping(target = "hasPendingUnjoinRequest",
            expression = "java(campaignMember.getUnjoinRequestedAt() != null)")
    public abstract JoinedCampaignResponse toJoinedResponse(CampaignMember campaignMember,
            @org.mapstruct.Context CampaignAggregatesContext context,
            @org.mapstruct.Context Map<Long, String> coverImageMap);

    @AfterMapping
    void enrichResponse(CampaignMember source,
            @MappingTarget JoinedCampaignResponse.JoinedCampaignResponseBuilder builder,
            @org.mapstruct.Context CampaignAggregatesContext context,
            @org.mapstruct.Context Map<Long, String> coverImageMap) {

        if (source.getCampaign() == null) {
            return;
        }
        Long campaignId = source.getCampaign().getId();

        if (coverImageMap != null) {
            builder.coverImageUrl(coverImageMap.get(campaignId));
        }

        if (context != null) {
            java.util.Map<Long, Long> amountsMap = context.getAmountsMap();
            java.util.Map<Long, Long> donorsMap = context.getDonorsMap();
            java.util.Map<Long, Long> volunteersMap = context.getVolunteersMap();

            builder.currentRaised(amountsMap != null ? amountsMap.getOrDefault(campaignId, 0L) : 0L);
            builder.donorsCount(donorsMap != null ? donorsMap.getOrDefault(campaignId, 0L) : 0L);
            builder.volunteersCount(volunteersMap != null ? volunteersMap.getOrDefault(campaignId, 0L) : 0L);
        }
    }
}
