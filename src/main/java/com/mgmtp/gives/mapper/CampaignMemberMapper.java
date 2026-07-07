package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.entity.CampaignMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CampaignMemberMapper {

    @Mapping(target = "campaignId", source = "campaign.id")
    @Mapping(target = "title", source = "campaign.title")
    @Mapping(target = "description", source = "campaign.description")
    @Mapping(target = "status", source = "campaign.status")
    @Mapping(target = "startDate", source = "campaign.startDate")
    @Mapping(target = "endDate", source = "campaign.endDate")
    @Mapping(target = "target", source = "campaign.target")
    @Mapping(target = "priority", source = "campaign.priority")
    @Mapping(target = "role", source = "roleInCampaign")
    JoinedCampaignResponse toJoinedResponse(CampaignMember campaignMember);
}
