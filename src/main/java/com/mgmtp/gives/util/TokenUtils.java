package com.mgmtp.gives.util;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.exception.AppException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class TokenUtils {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_TOKEN_BYTE_LENGTH = 32;
    private static final String BEARER_PREFIX = "Bearer ";

    private TokenUtils() {
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Configuration failure");
        }
    }

    public static String generateSecureToken() {
        byte[] randomBytes = new byte[DEFAULT_TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
