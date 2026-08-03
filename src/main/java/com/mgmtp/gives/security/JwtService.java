package com.mgmtp.gives.security;

import com.mgmtp.gives.common.JwtProps;
import com.mgmtp.gives.dto.auth.TokenGenerationRequest;
import com.mgmtp.gives.exception.AppException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import static com.mgmtp.gives.common.ErrorCode.EXPIRED_TOKEN;
import static com.mgmtp.gives.common.ErrorCode.INVALID_TOKEN;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProps jwtProps;

    public String generateAccessToken(TokenGenerationRequest request) {
        Map<String, Object> claims = Map.of("role", request.role(), "userId", request.userId());
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProps.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(request.email())
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public Claims extractClaimsFromToken(String token) {
        try {
            return extractAllClaim(token);
        } catch (SignatureException ex) {
            throw new AppException(INVALID_TOKEN, "Invalid token signature");
        } catch (MalformedJwtException ex) {
            throw new AppException(INVALID_TOKEN, "Token format is invalid");
        } catch (ExpiredJwtException ex) {
            throw new AppException(EXPIRED_TOKEN, "Token has expired");
        } catch (UnsupportedJwtException ex) {
            throw new AppException(INVALID_TOKEN, "Token type is not supported");
        } catch (IllegalArgumentException ex) {
            throw new AppException(INVALID_TOKEN, "Token claims are empty");
        }
    }

    public String extractEmailFromToken(String token) {
        return extract(token, Claims::getSubject);
    }

    public Long extractUserIdFromToken(String token) {
        return extract(token, claims -> claims.get("userId", Long.class));
    }

    public String extractRoleFromToken(String token) {
        return extract(token, claims -> claims.get("role", String.class));
    }

    private <T> T extract(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaim(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaim(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProps.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
