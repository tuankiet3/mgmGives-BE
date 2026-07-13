package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.common.WebexProps;
import com.mgmtp.gives.dto.webex.WebexAuthorizeUrlResponse;
import com.mgmtp.gives.dto.webex.WebexConnectionStatusResponse;
import com.mgmtp.gives.dto.webex.WebexPersonResponse;
import com.mgmtp.gives.dto.webex.WebexTokenResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserWebexConnection;
import com.mgmtp.gives.entity.WebexOAuthState;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.UserWebexConnectionRepository;
import com.mgmtp.gives.repository.WebexOAuthStateRepository;
import com.mgmtp.gives.service.TokenCryptoService;
import com.mgmtp.gives.service.UserWebexConnectionService;
import com.mgmtp.gives.service.WebexTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserWebexConnectionServiceImpl implements UserWebexConnectionService {
    private static final int STATE_TTL_MINUTES = 10;
    private static final int TOKEN_REFRESH_SKEW_SECONDS = 60;
    private static final String DEFAULT_WEBEX_RETURN_TO = "/integration-settings";

    private final WebexProps webexProps;
    private final MailProps mailProps;
    private final WebexTokenService webexTokenService;
    private final UserWebexConnectionRepository userWebexConnectionRepository;
    private final WebexOAuthStateRepository webexOAuthStateRepository;
    private final TokenCryptoService tokenCryptoService;
    private final RestClient restClient;

    @Override
    @Transactional
    public WebexAuthorizeUrlResponse createAuthorizeUrl(User currentUser, String returnTo) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User must be authenticated");
        }

        String state = generateState();
        String safeReturnTo = safeLocalReturnTo(returnTo);
        webexOAuthStateRepository.save(WebexOAuthState.builder()
                .state(state)
                .user(currentUser)
                .returnTo(safeReturnTo)
                .expiresAt(LocalDateTime.now().plusMinutes(STATE_TTL_MINUTES))
                .build());
        log.info("Created Webex OAuth state. userId={}, expiresInMinutes={}, returnTo={}",
                currentUser.getId(), STATE_TTL_MINUTES, safeReturnTo);

        String authorizeUrl = UriComponentsBuilder
                .fromUriString("https://webexapis.com/v1/authorize")
                .queryParam("client_id", webexProps.getClientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", webexProps.getRedirectUri())
                .queryParam("scope", webexProps.getScopes())
                .queryParam("state", state)
                .toUriString();
        log.debug("Built Webex authorize URL. userId={}, redirectUri={}, scopes={}",
                currentUser.getId(), webexProps.getRedirectUri(), webexProps.getScopes());

        return new WebexAuthorizeUrlResponse(authorizeUrl);
    }

    @Override
    @Transactional
    public String handleCallback(String code, String state) {
        WebexOAuthState oauthState = validateState(state);
        log.info("Validated Webex OAuth state. stateId={}, userId={}",
                oauthState.getId(), oauthState.getUser().getId());
        WebexTokenResponse tokenResponse = webexTokenService.exchangeAuthorizationCode(code);
        validateAuthorizationTokenResponse(tokenResponse);
        log.info("Exchanged Webex authorization code. userId={}, accessExpiresIn={}, refreshExpiresIn={}",
                oauthState.getUser().getId(), tokenResponse.expiresIn(), tokenResponse.refreshTokenExpiresIn());

        WebexPersonResponse person = getCurrentWebexPerson(tokenResponse.accessToken());
        log.info("Loaded Webex profile. userId={}, webexPersonId={}, webexEmail={}",
                oauthState.getUser().getId(), person != null ? person.id() : null, primaryEmail(person));
        LocalDateTime now = LocalDateTime.now();
        UserWebexConnection connection = userWebexConnectionRepository.findByUserId(oauthState.getUser().getId())
                .orElseGet(() -> UserWebexConnection.builder()
                        .user(oauthState.getUser())
                        .connectedAt(now)
                        .build());

        connection.setWebexPersonId(person != null ? person.id() : null);
        connection.setWebexEmail(primaryEmail(person));
        connection.setAccessToken(tokenCryptoService.encrypt(tokenResponse.accessToken()));
        connection.setRefreshToken(tokenCryptoService.encrypt(tokenResponse.refreshToken()));
        connection.setAccessTokenExpiresAt(expiresAt(now, tokenResponse.expiresIn()));
        connection.setRefreshTokenExpiresAt(expiresAt(now, tokenResponse.refreshTokenExpiresIn()));
        connection.setUpdatedAt(now);
        if (connection.getConnectedAt() == null) {
            connection.setConnectedAt(now);
        }
        userWebexConnectionRepository.save(connection);
        log.info("Saved Webex connection. userId={}, connectionId={}, webexEmail={}",
                oauthState.getUser().getId(), connection.getId(), connection.getWebexEmail());

        oauthState.setUsedAt(now);
        webexOAuthStateRepository.save(oauthState);
        log.debug("Marked Webex OAuth state as used. stateId={}, userId={}",
                oauthState.getId(), oauthState.getUser().getId());

        return UriComponentsBuilder
                .fromUriString(frontendUrl(safeLocalReturnTo(oauthState.getReturnTo())))
                .queryParam("webex", "success")
                .toUriString();
    }

    @Override
    @Transactional(readOnly = true)
    public WebexConnectionStatusResponse getStatus(User currentUser) {
        return userWebexConnectionRepository.findByUserId(currentUser.getId())
                .map(connection -> new WebexConnectionStatusResponse(
                        true,
                        connection.getWebexEmail(),
                        connection.getWebexPersonId(),
                        connection.getConnectedAt()
                ))
                .orElseGet(() -> new WebexConnectionStatusResponse(false, null, null, null));
    }

    @Override
    @Transactional
    public void disconnect(User currentUser) {
        userWebexConnectionRepository.deleteByUserId(currentUser.getId());
        log.info("Deleted Webex connection. userId={}", currentUser.getId());
    }

    @Override
    @Transactional
    public String getValidAccessToken(User user) {
        if (user == null || user.getId() == null) {
            throw new AppException(ErrorCode.WEBEX_NOT_CONNECTED);
        }
        UserWebexConnection connection = userWebexConnectionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.WEBEX_NOT_CONNECTED));

        LocalDateTime now = LocalDateTime.now();
        if (StringUtils.hasText(connection.getAccessToken())
                && connection.getAccessTokenExpiresAt() != null
                && connection.getAccessTokenExpiresAt().isAfter(now.plusSeconds(TOKEN_REFRESH_SKEW_SECONDS))) {
            log.debug("Using cached Webex access token. userId={}, expiresAt={}",
                    user.getId(), connection.getAccessTokenExpiresAt());
            return tokenCryptoService.decrypt(connection.getAccessToken());
        }
        if (connection.getRefreshTokenExpiresAt() != null && !connection.getRefreshTokenExpiresAt().isAfter(now)) {
            log.warn("Webex refresh token expired. userId={}, refreshExpiresAt={}",
                    user.getId(), connection.getRefreshTokenExpiresAt());
            throw new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Webex connection expired. Please reconnect Webex.");
        }

        log.info("Refreshing Webex access token. userId={}", user.getId());
        WebexTokenResponse tokenResponse = webexTokenService.refreshAccessToken(
                tokenCryptoService.decrypt(connection.getRefreshToken())
        );
        validateAccessTokenResponse(tokenResponse);
        connection.setAccessToken(tokenCryptoService.encrypt(tokenResponse.accessToken()));
        if (StringUtils.hasText(tokenResponse.refreshToken())) {
            connection.setRefreshToken(tokenCryptoService.encrypt(tokenResponse.refreshToken()));
        }
        connection.setAccessTokenExpiresAt(expiresAt(now, tokenResponse.expiresIn()));
        if (tokenResponse.refreshTokenExpiresIn() != null) {
            connection.setRefreshTokenExpiresAt(expiresAt(now, tokenResponse.refreshTokenExpiresIn()));
        }
        connection.setUpdatedAt(now);
        userWebexConnectionRepository.save(connection);
        log.info("Refreshed Webex access token. userId={}, accessExpiresAt={}, refreshExpiresAt={}",
                user.getId(), connection.getAccessTokenExpiresAt(), connection.getRefreshTokenExpiresAt());
        return tokenCryptoService.decrypt(connection.getAccessToken());
    }

    private WebexOAuthState validateState(String state) {
        if (!StringUtils.hasText(state)) {
            throw new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Missing Webex OAuth state");
        }
        WebexOAuthState oauthState = webexOAuthStateRepository.findByState(state)
                .orElseThrow(() -> new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Invalid Webex OAuth state"));
        if (oauthState.getUsedAt() != null) {
            log.warn("Rejected used Webex OAuth state. stateId={}, userId={}",
                    oauthState.getId(), oauthState.getUser() != null ? oauthState.getUser().getId() : null);
            throw new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Webex OAuth state was already used");
        }
        if (oauthState.getExpiresAt() == null || !oauthState.getExpiresAt().isAfter(LocalDateTime.now())) {
            log.warn("Rejected expired Webex OAuth state. stateId={}, userId={}, expiresAt={}",
                    oauthState.getId(),
                    oauthState.getUser() != null ? oauthState.getUser().getId() : null,
                    oauthState.getExpiresAt());
            throw new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Webex OAuth state expired");
        }
        return oauthState;
    }

    private WebexPersonResponse getCurrentWebexPerson(String accessToken) {
        try {
            return restClient
                    .get()
                    .uri(webexProps.getApiBaseUrl() + "/people/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(WebexPersonResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("Failed to load Webex profile. status={}, response={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AppException(
                    ErrorCode.WEBEX_AUTHORIZATION_FAILED,
                    "Failed to load Webex profile: " + ex.getResponseBodyAsString()
            );
        }
    }

    private void validateAuthorizationTokenResponse(WebexTokenResponse tokenResponse) {
        if (tokenResponse == null
                || !StringUtils.hasText(tokenResponse.accessToken())
                || !StringUtils.hasText(tokenResponse.refreshToken())) {
            log.warn("Invalid Webex authorization token response. hasAccessToken={}, hasRefreshToken={}",
                    tokenResponse != null && StringUtils.hasText(tokenResponse.accessToken()),
                    tokenResponse != null && StringUtils.hasText(tokenResponse.refreshToken()));
            throw new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Webex did not return valid tokens");
        }
    }

    private void validateAccessTokenResponse(WebexTokenResponse tokenResponse) {
        if (tokenResponse == null || !StringUtils.hasText(tokenResponse.accessToken())) {
            log.warn("Invalid Webex refresh token response. hasAccessToken={}",
                    tokenResponse != null && StringUtils.hasText(tokenResponse.accessToken()));
            throw new AppException(ErrorCode.WEBEX_AUTHORIZATION_FAILED, "Webex did not return a valid access token");
        }
    }

    private LocalDateTime expiresAt(LocalDateTime now, Long expiresInSeconds) {
        return expiresInSeconds == null ? null : now.plusSeconds(expiresInSeconds);
    }

    private String primaryEmail(WebexPersonResponse person) {
        if (person == null || person.emails() == null || person.emails().isEmpty()) {
            return null;
        }
        return person.emails().get(0);
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String safeLocalReturnTo(String returnTo) {
        if (!StringUtils.hasText(returnTo)
                || !returnTo.startsWith("/")
                || returnTo.startsWith("//")
                || returnTo.contains("://")) {
            return DEFAULT_WEBEX_RETURN_TO;
        }

        return returnTo;
    }

    private String frontendUrl(String path) {
        return mailProps.getFrontendUrl().replaceAll("/+$", "") + path;
    }
}
