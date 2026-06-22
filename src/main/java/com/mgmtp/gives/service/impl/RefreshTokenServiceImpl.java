package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.JwtProps;
import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.RefreshToken;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.AuthMapper;
import com.mgmtp.gives.repository.RefreshTokenRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.security.JwtService;
import com.mgmtp.gives.service.RefreshTokenService;
import com.mgmtp.gives.util.CookieUtils;
import com.mgmtp.gives.util.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.mgmtp.gives.common.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepo;
    private final UserRepository userRepo;
    private final JwtProps jwtProps;
    private final JwtService jwtService;
    private final AuthMapper authMapper;

    @Override
    @Transactional
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


    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME)
                .orElseThrow(() -> new AppException(INVALID_REFRESH_TOKEN));

        RefreshToken refreshToken = validate(rawRefreshToken);

        User user = refreshToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(UNAUTHORIZED);
        }

        String newAccessToken = jwtService.generateAccessToken(authMapper.toTokenGenerationRequest(user));

        CookieUtils.addAccessTokenCookie(
                response,
                newAccessToken,
                jwtProps.getAccessTokenExpiration()
        );
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        String hashedToken = TokenUtils.hash(rawRefreshToken);

        refreshTokenRepo.findByTokenHash(hashedToken)
                .ifPresent(refreshToken -> {
                    refreshToken.setIsRevoked(true);
                });
    }


}
