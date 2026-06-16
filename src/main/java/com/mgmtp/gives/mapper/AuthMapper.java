package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.User;

public interface AuthMapper {
    TokenGenerationRequest toTokenGenerationRequest(User user);
}
