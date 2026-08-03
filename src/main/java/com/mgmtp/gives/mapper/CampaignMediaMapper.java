package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.entity.CampaignMedia;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CampaignMediaMapper {
    @Mapping(target = "isCover", source = "cover")
    CampaignMediaResponse toResponse(CampaignMedia media);

    @InheritConfiguration(name = "toResponse")
    List<CampaignMediaResponse> toResponseList(List<CampaignMedia> medias);
}
