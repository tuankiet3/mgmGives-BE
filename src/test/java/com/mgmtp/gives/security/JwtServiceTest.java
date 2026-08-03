package com.mgmtp.gives.security;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.JwtProps;
import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "test-only-jwt-secret-test-only-jwt-secret-test-only-jwt-secret-test-only-jwt-secret";

    private JwtProps jwtProps;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtProps = new JwtProps();
        jwtProps.setSecret(TEST_SECRET);
        jwtProps.setAccessTokenExpiration(60_000);
        jwtProps.setRefreshTokenExpiration(120_000);
        jwtService = new JwtService(jwtProps);
    }

    @Test
    void generatedTokenRoundTripsIdentityClaims() {
        String token = jwtService.generateAccessToken(new TokenGenerationRequest(
                42L,
                "member@example.test",
                "USER"
        ));

        assertThat(jwtService.extractEmailFromToken(token)).isEqualTo("member@example.test");
        assertThat(jwtService.extractUserIdFromToken(token)).isEqualTo(42L);
        assertThat(jwtService.extractRoleFromToken(token)).isEqualTo("USER");
    }

    @Test
    void expiredTokenMapsToExpiredTokenError() {
        jwtProps.setAccessTokenExpiration(-1);
        String token = jwtService.generateAccessToken(new TokenGenerationRequest(
                42L,
                "member@example.test",
                "USER"
        ));

        assertThatThrownBy(() -> jwtService.extractClaimsFromToken(token))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXPIRED_TOKEN));
    }

    @RepeatedTest(32)
    void modifiedSignatureMapsToInvalidTokenError(RepetitionInfo repetitionInfo) {
        String token = jwtService.generateAccessToken(new TokenGenerationRequest(
                42L,
                "member+" + repetitionInfo.getCurrentRepetition() + "@example.test",
                "USER"
        ));
        String modifiedToken = tamperSignature(token);

        assertInvalidToken(modifiedToken);
    }

    @Test
    void malformedTokenMapsToInvalidTokenError() {
        assertInvalidToken("not-a-jwt");
    }

    private void assertInvalidToken(String token) {
        assertThatThrownBy(() -> jwtService.extractClaimsFromToken(token))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    private String tamperSignature(String token) {
        String[] segments = token.split("\\.", -1);
        assertThat(segments).hasSize(3);
        assertThat(segments[2]).isNotEmpty();

        char replacement = segments[2].charAt(0) == 'A' ? 'B' : 'A';
        segments[2] = replacement + segments[2].substring(1);
        return String.join(".", segments);
    }
}
