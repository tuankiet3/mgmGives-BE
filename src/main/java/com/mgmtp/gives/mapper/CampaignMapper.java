package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CategoryMapper.class)
public interface CampaignMapper {

    @Mapping(target = "creatorId", source = "user.id")
    @Mapping(target = "creatorName", source = "user.fullName")
    @Mapping(target = "categories", source = "categories")
    CampaignResponse toResponse(Campaign campaign);
}
