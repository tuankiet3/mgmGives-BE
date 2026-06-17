package com.mgmtp.gives.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AuthResponse {
    private String accessToken;
    private LocalDateTime accessTokenExpiresAt;
    private String refreshToken;
}
