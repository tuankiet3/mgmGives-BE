package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.notification.NotificationResponse;
import com.mgmtp.gives.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(source = "read", target = "isRead")
    NotificationResponse toNotificationResponse(Notification notification);
}
