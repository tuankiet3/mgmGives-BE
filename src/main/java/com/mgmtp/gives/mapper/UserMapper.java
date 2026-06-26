package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.user.AdminUserResponse;
import com.mgmtp.gives.dto.user.AdminUpdateUserRequest;
import com.mgmtp.gives.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;


@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface UserMapper {
    AdminUserResponse toAdminUserResponse(User user);

    void updateEntityFromRequest(AdminUpdateUserRequest request, @MappingTarget User user);
}
