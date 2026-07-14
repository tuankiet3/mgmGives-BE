package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.announcement.AnnouncementReplyResponse;
import com.mgmtp.gives.entity.AnnouncementReply;
import com.mgmtp.gives.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AnnouncementReplyMapper {

    @Mapping(target = "announcementId", source = "announcement.id")
    @Mapping(target = "createdBy", source = "user")
    @Mapping(target = "isEdited", source = "edited")
    AnnouncementReplyResponse toResponse(AnnouncementReply reply);

    @Mapping(target = "name", source = "fullName")
    AnnouncementReplyResponse.UserSummary toUserSummary(User user);
}
