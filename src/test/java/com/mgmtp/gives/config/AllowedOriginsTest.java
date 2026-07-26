package com.mgmtp.gives.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedOriginsTest {
    @Test
    void shouldTrimAndDeduplicateOrigins() {
        assertThat(AllowedOrigins.parse(
                " https://mgm-gives.vercel.app, http://localhost:5173,https://mgm-gives.vercel.app "))
                .containsExactly("https://mgm-gives.vercel.app", "http://localhost:5173");
    }

    @Test
    void shouldRejectBlankOrigins() {
        assertThatThrownBy(() -> AllowedOrigins.parse(" , "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectGlobalWildcard() {
        assertThatThrownBy(() -> AllowedOrigins.parse("*"))
                .isInstanceOf(IllegalStateException.class);
    }
}
