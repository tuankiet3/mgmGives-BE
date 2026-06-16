package com.mgmtp.gives.service.impl;

import static com.mgmtp.gives.common.ErrorCode.*;
import com.mgmtp.gives.common.JwtProps;
import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.RefreshToken;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.RefreshTokenRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.RefreshTokenService;
import com.mgmtp.gives.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.mgmtp.gives.common.ErrorCode.USER_NOT_FOUND;

@Service @RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepo;
    private final UserRepository userRepo;
    private final JwtProps jwtProps;

    @Override @Transactional
    public String generate(TokenGenerationRequest request) {
        String token = TokenUtils.generateSecureToken();

        User user = userRepo.findByEmail(request.email())
                .orElseThrow(() -> new AppException(USER_NOT_FOUND));

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(TokenUtils.hash(token)).isRevoked(false)
                .expiresAt(LocalDateTime.now().plus(jwtProps.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                .user(user).build();

        refreshTokenRepo.save(refreshToken);

        return token;
    }


    @Override @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(INVALID_REFRESH_TOKEN, "Refresh token cannot be null or empty");
        }

        RefreshToken refreshToken = refreshTokenRepo.findByTokenHash(TokenUtils.hash(rawToken))
                .orElseThrow(() -> new AppException(INVALID_REFRESH_TOKEN));

        if (refreshToken.getIsRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(INVALID_REFRESH_TOKEN);
        }

        return refreshToken;
    }

}
