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
    @Mapping(target = "inReplyTo", source = "inReplyTo")
    AnnouncementReplyResponse toResponse(AnnouncementReply reply);

    @Mapping(target = "name", source = "fullName")
    AnnouncementReplyResponse.UserSummary toUserSummary(User user);

    default AnnouncementReplyResponse.ReplyReference toReplyReference(AnnouncementReply reply) {
        if (reply == null) {
            return null;
        }

        boolean isDeleted = reply.getDeletedAt() != null;
        // Keep the relationship visible after a soft delete without exposing deleted content or author details.
        return new AnnouncementReplyResponse.ReplyReference(
                reply.getId(),
                isDeleted ? null : toUserSummary(reply.getUser()),
                isDeleted ? null : reply.getContent(),
                isDeleted
        );
    }
}
