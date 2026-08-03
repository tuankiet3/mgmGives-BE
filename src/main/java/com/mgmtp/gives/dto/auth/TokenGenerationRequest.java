package com.mgmtp.gives.dto.auth;

public record TokenGenerationRequest(
        Long userId,
        String email,
        String role
) {
}

