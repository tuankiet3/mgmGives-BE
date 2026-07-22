package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.WebexProps;
import com.mgmtp.gives.dto.webex.WebexTokenResponse;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.service.WebexTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebexTokenServiceImpl implements WebexTokenService {
    private final WebexProps webexProps;
    private final RestClient restClient;

    @Override
    public WebexTokenResponse exchangeAuthorizationCode(String code) {
        log.info("Exchanging Webex authorization code.");
        MultiValueMap<String, String> body = baseTokenRequest();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", webexProps.getRedirectUri());

        return requestToken(body, "Failed to exchange Webex authorization code");
    }

    @Override
    public WebexTokenResponse refreshAccessToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new AppException(ErrorCode.WEBEX_NOT_CONNECTED);
        }
        log.info("Refreshing Webex token.");
        MultiValueMap<String, String> body = baseTokenRequest();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        return requestToken(body, "Failed to refresh Webex access token");
    }

    private MultiValueMap<String, String> baseTokenRequest() {
        if (!StringUtils.hasText(webexProps.getClientId()) || !StringUtils.hasText(webexProps.getClientSecret())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Webex client ID and client secret must be configured");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", webexProps.getClientId());
        body.add("client_secret", webexProps.getClientSecret());
        return body;
    }

    private WebexTokenResponse requestToken(MultiValueMap<String, String> body, String failureMessage) {
        try {
            return restClient
                    .post()
                    .uri(webexProps.getApiBaseUrl() + "/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(WebexTokenResponse.class);
        } catch (RestClientResponseException ex) {
            log.warn("Webex token request failed. status={}, response={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new AppException(
                    ErrorCode.UNAUTHORIZED,
                    failureMessage + ": " + ex.getResponseBodyAsString()
            );
        }
    }
}
