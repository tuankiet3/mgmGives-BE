package com.mgmtp.gives.dto.integration;

import jakarta.validation.constraints.NotBlank;

public record PayOSConnectionRequest(
        @NotBlank(message = "Client ID is required")
        String clientId,

        @NotBlank(message = "API Key is required")
        String apiKey,

        @NotBlank(message = "Checksum Key is required")
        String checksumKey
) {}
