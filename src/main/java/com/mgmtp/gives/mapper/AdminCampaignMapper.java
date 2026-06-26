package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign.AdminCampaignResponse;
import com.mgmtp.gives.dto.campaign.CampaignOwnerResponse;
import com.mgmtp.gives.entity.Campaign;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public interface AdminCampaignMapper {

    @Mapping(target = "creatorId", source = "user.id")
    @Mapping(target = "creatorName", source = "user.fullName")
    @Mapping(target = "creatorEmail", source = "user.email")
    @Mapping(target = "approvedById", source = "approvedBy.id")
    @Mapping(target = "approvedByName", source = "approvedBy.fullName")
    @Mapping(target = "categories", source = "categories")
    @Mapping(target = "medias", ignore = true)
    AdminCampaignResponse toAdminResponse(Campaign campaign);

    @Mapping(target = "creatorId", source = "user.id")
    @Mapping(target = "creatorName", source = "user.fullName")
    @Mapping(target = "categories", source = "categories")
    CampaignOwnerResponse toOwnerResponse(Campaign campaign);
}
