package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.RefreshToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface RefreshTokenService {
    RefreshToken validate(String refreshToken);

    String generate(TokenGenerationRequest request);

    void refresh(HttpServletRequest request, HttpServletResponse response);

    void revoke(String rawRefreshToken);
}
