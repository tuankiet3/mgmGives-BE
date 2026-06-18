package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.dto.auth.UserInfoResponse;
import com.mgmtp.gives.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {
    @Mapping(target = "userId", source = "id")
    TokenGenerationRequest toTokenGenerationRequest(User user);

    UserInfoResponse toUserInfoResponse(User user);
}
