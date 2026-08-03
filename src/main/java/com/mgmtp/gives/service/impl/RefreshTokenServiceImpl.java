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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.mgmtp.gives.common.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepo;
    private final UserRepository userRepo;
    private final JwtProps jwtProps;
    private final JwtService jwtService;
    private final AuthMapper authMapper;

    @Override
    @Transactional
    public String generate(TokenGenerationRequest request) {
        User user = userRepo.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Generate refresh token failed: user not found. email={}", request.email());
                    return new AppException(USER_NOT_FOUND);
                });

        return generateForUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            log.warn("Refresh token validation failed: token is null or blank");
            throw new AppException(INVALID_REFRESH_TOKEN, "Refresh token cannot be null or empty");
        }

        RefreshToken refreshToken = refreshTokenRepo.findByTokenHash(TokenUtils.hash(rawToken))
                .orElseThrow(() -> {
                    log.warn("Refresh token validation failed: token not found");
                    return new AppException(INVALID_REFRESH_TOKEN);
                });

        if (refreshToken.getIsRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token validation failed: revoked={}, expiresAt={}, userId={}",
                    refreshToken.getIsRevoked(), refreshToken.getExpiresAt(), refreshToken.getUser().getId());
            throw new AppException(INVALID_REFRESH_TOKEN);
        }

        return refreshToken;
    }

    @Override
    @Transactional
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME)
                        .orElseThrow(() -> {
                            log.warn("Refresh failed: no refresh token cookie present");
                            return new AppException(INVALID_REFRESH_TOKEN);
                        });

        RefreshToken oldRefreshToken = validate(rawRefreshToken);
        User user = oldRefreshToken.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Refresh failed: user not active. userId={}, status={}", user.getId(), user.getStatus());
            throw new AppException(UNAUTHORIZED);
        }
        oldRefreshToken.setIsRevoked(true);

        String newRefreshToken = generateForUser(user);
        String newAccessToken = jwtService.generateAccessToken(authMapper.toTokenGenerationRequest(user));

        CookieUtils.addAccessTokenCookie(response, newAccessToken, jwtProps.getAccessTokenExpiration());
        CookieUtils.addRefreshCookie(response, newRefreshToken, jwtProps.getRefreshTokenExpiration());

        log.info("Token refreshed successfully. userId={}", user.getId());
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        String hashedToken = TokenUtils.hash(rawRefreshToken);

        refreshTokenRepo.findByTokenHash(hashedToken)
                .ifPresentOrElse(
                        refreshToken -> {
                            refreshToken.setIsRevoked(true);
                            log.info("Refresh token revoked. userId={}", refreshToken.getUser().getId());
                        },
                        () -> log.warn("Revoke skipped: token not found")
                );
    }

    private String generateForUser(User user) {
        String rawToken = TokenUtils.generateSecureToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(TokenUtils.hash(rawToken))
                .isRevoked(false)
                .expiresAt(LocalDateTime.now().plus(jwtProps.getRefreshTokenExpiration(), ChronoUnit.MILLIS))
                .user(user)
                .build();

        refreshTokenRepo.save(refreshToken);
        log.debug("New refresh token generated. userId={}", user.getId());

        return rawToken;
    }
}
