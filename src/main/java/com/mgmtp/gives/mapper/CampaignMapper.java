package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.DonationRepository;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public abstract class CampaignMapper {

    @Autowired
    protected DonationRepository donationRepository;

    @Autowired
    protected CampaignMemberRepository campaignMemberRepository;

    @Mapping(target = "creatorId", source = "user.id")
    @Mapping(target = "creatorName", source = "user.fullName")
    @Mapping(target = "creatorAvatarUrl", source = "user.avatarUrl")
    @Mapping(target = "categories", source = "categories")
    @Mapping(target = "currentRaised", expression = "java(calculateCurrentRaised(campaign))")
    @Mapping(target = "medias", ignore = true)
    @Mapping(target = "media", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "isEditable", ignore = true)
    @Mapping(target = "isFollowed", ignore = true)
    @Mapping(target = "isJoined", ignore = true)
    @Mapping(target = "hasPendingUnjoinRequest", ignore = true)
    @Mapping(target = "volunteersCount", ignore = true)
    @Mapping(target = "donorsCount", ignore = true)
    @Mapping(target = "isCampaignAdmin", expression = "java(resolveIsCampaignAdmin(campaign, currentUserId, isSystemAdmin))")
    @Mapping(target = "resultPosted", source = "resultPosted")
    @Mapping(target = "resultPublishedAt", source = "resultPublishedAt")
    @Mapping(target = "resultPublishedByName", expression = "java(campaign.getResultPublishedBy() != null ? campaign.getResultPublishedBy().getFullName() : null)")
    @Mapping(target = "creatorHasPayOS", ignore = true)
    public abstract CampaignResponse toResponse(Campaign campaign, @Context Long currentUserId,
            @Context boolean isSystemAdmin);

    protected Long calculateCurrentRaised(Campaign campaign) {
        if (campaign == null || campaign.getId() == null) {
            return 0L;
        }
        return donationRepository.sumConfirmedAmountByCampaignId(campaign.getId());
    }

    protected boolean resolveIsCampaignAdmin(Campaign campaign, Long currentUserId, boolean isSystemAdmin) {
        if (currentUserId == null || campaign == null || campaign.getId() == null)
            return false;
        return campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaign.getId(), currentUserId, CampaignMemberRole.CAMPAIGN_ADMIN);
    }
}
