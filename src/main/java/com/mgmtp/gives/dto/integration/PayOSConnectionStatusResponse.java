package com.mgmtp.gives.dto.integration;

import java.time.LocalDateTime;

public record PayOSConnectionStatusResponse(
        boolean connected,
        String clientId,
        LocalDateTime connectedAt
) {}
