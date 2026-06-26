package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.entity.CampaignMedia;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CampaignMediaMapper {
    CampaignMediaResponse toResponse(CampaignMedia media);
    List<CampaignMediaResponse> toResponseList(List<CampaignMedia> medias);
}
