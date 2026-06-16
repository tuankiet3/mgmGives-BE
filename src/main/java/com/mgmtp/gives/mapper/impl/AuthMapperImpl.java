package com.mgmtp.gives.mapper.impl;

import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.mapper.AuthMapper;
import org.springframework.stereotype.Component;

@Component
public class AuthMapperImpl implements AuthMapper {
    @Override
    public TokenGenerationRequest toTokenGenerationRequest(User user) {
        return new TokenGenerationRequest(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
