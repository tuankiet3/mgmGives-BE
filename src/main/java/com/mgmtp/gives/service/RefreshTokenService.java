package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.RefreshToken;

public interface RefreshTokenService {
    RefreshToken validate(String refreshToken);

    String generate(TokenGenerationRequest request);
}
