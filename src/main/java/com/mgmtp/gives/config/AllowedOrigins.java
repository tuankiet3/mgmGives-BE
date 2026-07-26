package com.mgmtp.gives.config;

import java.util.Arrays;

final class AllowedOrigins {
    private AllowedOrigins() {
    }

    static String[] parse(String rawOrigins) {
        if (rawOrigins == null) {
            throw new IllegalStateException("APP_ALLOWED_ORIGINS must be configured");
        }

        String[] origins = Arrays.stream(rawOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);

        if (origins.length == 0 || Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException(
                    "APP_ALLOWED_ORIGINS must contain explicit origins; a global wildcard is not allowed");
        }

        return origins;
    }
}
