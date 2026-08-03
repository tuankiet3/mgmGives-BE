package com.mgmtp.gives.security;

import com.mgmtp.gives.common.JwtProps;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.mapper.AuthMapper;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.RefreshTokenService;
import com.mgmtp.gives.util.CookieUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepo;
    private final AuthMapper authMapper;
    private final JwtProps jwtProps;
    private final MailProps mailProps;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        String email = oauthUser.getAttribute("email");
        if (!StringUtils.hasText(email)) {
            response.sendRedirect(mailProps.getFrontendUrl() + "/login?oauthError=missing_email");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseGet(() -> createOAuthUser(oauthUser, normalizedEmail));

        if (UserStatus.INACTIVE.equals(user.getStatus())) {
            user.setStatus(UserStatus.ACTIVE);
            userRepo.save(user);
        } else if (UserStatus.BANNED.equals(user.getStatus())) {
            response.sendRedirect(mailProps.getFrontendUrl() + "/login?oauthError=account_banned");
            return;
        }

        TokenGenerationRequest req = authMapper.toTokenGenerationRequest((user));

        String accessToken = jwtService.generateAccessToken(req);
        String refreshToken = refreshTokenService.generate(req);

        CookieUtils.addAccessTokenCookie(response, accessToken, jwtProps.getAccessTokenExpiration());
        CookieUtils.addRefreshCookie(response, refreshToken, jwtProps.getRefreshTokenExpiration());

        String redirectPath = UserRole.ADMIN.equals(user.getRole())
                ? "/admin"
                : getSafeOAuthRedirectPath(request);
        CookieUtils.clearOAuthRedirectCookie(response);
        response.sendRedirect(mailProps.getFrontendUrl() + redirectPath);
    }

    private String getSafeOAuthRedirectPath(HttpServletRequest request) {
        return CookieUtils.getCookieValue(request, CookieUtils.OAUTH_REDIRECT_COOKIE_NAME)
                .map(this::decodeRedirectPath)
                .filter(this::isSafeLocalPath)
                .orElse("/dashboard");
    }

    private String decodeRedirectPath(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private boolean isSafeLocalPath(String path) {
        return StringUtils.hasText(path)
                && path.startsWith("/")
                && !path.startsWith("//")
                && !path.contains("\r")
                && !path.contains("\n");
    }

    private User createOAuthUser(OAuth2User oauthUser, String email) {
        String fullName = oauthUser.getAttribute("name");
        if (!StringUtils.hasText(fullName)) {
            fullName = email;
        }

        return userRepo.save(User.builder()
                .email(email)
                .fullName(fullName)
                .avatarUrl(oauthUser.getAttribute("picture"))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }
}
