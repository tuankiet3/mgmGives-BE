package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign.AdminCampaignResponse;
import com.mgmtp.gives.dto.campaign.CampaignOwnerResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.repository.DonationRepository;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public abstract class AdminCampaignMapper {

    @Autowired
    protected DonationRepository donationRepository;

    @Mapping(target = "creatorId", source = "user.id")
    @Mapping(target = "creatorName", source = "user.fullName")
    @Mapping(target = "creatorAvatarUrl", source = "user.avatarUrl")
    @Mapping(target = "creatorEmail", source = "user.email")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "categories", source = "categories")
    @Mapping(target = "currentRaised", expression = "java(calculateCurrentRaised(campaign))")
    @Mapping(target = "medias", ignore = true)
    public abstract AdminCampaignResponse toAdminResponse(Campaign campaign);

    @Mapping(target = "creatorId", source = "user.id")
    @Mapping(target = "creatorName", source = "user.fullName")
    @Mapping(target = "creatorAvatarUrl", source = "user.avatarUrl")
    @Mapping(target = "categories", source = "categories")
    public abstract CampaignOwnerResponse toOwnerResponse(Campaign campaign);

    protected Long calculateCurrentRaised(Campaign campaign) {
        if (campaign == null || campaign.getId() == null) {
            return 0L;
        }
        return donationRepository.sumConfirmedAmountByCampaignId(campaign.getId());
    }
}
