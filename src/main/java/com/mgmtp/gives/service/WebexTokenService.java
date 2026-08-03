package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.webex.WebexTokenResponse;

public interface WebexTokenService {
    WebexTokenResponse exchangeAuthorizationCode(String code);

    WebexTokenResponse refreshAccessToken(String refreshToken);
}
