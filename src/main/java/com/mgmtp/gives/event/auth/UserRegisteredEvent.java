package com.mgmtp.gives.event.auth;

public record UserRegisteredEvent(
        String email,
        String fullName,
        String rawToken
) {
}
