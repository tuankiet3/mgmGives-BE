package com.mgmtp.gives.event;

public record UserRegisteredEvent(
        String email,
        String fullName,
        String rawToken
) {
}
