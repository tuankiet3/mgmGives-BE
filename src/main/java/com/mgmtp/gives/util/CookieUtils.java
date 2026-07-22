package com.mgmtp.gives.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.util.Arrays;
import java.util.Optional;

public class CookieUtils {
    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    public static final String OAUTH_REDIRECT_COOKIE_NAME = "mgm_gives_oauth_redirect";

    private CookieUtils() {
    }

    public static Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public static void addAccessTokenCookie(HttpServletResponse response, String accessToken, long maxAgeMillis) {
        addCookie(response, accessToken, ACCESS_TOKEN_COOKIE_NAME, maxAgeMillis);
    }

    public static void addRefreshCookie(HttpServletResponse response, String refreshToken, long maxAgeMillis) {
        addCookie(response, refreshToken, REFRESH_TOKEN_COOKIE_NAME, maxAgeMillis);
    }

    public static void clearAccessTokenCookie(HttpServletResponse response) {
        clearCookie(response, ACCESS_TOKEN_COOKIE_NAME);
    }

    public static void clearRefreshTokenCookie(HttpServletResponse response) {
        clearCookie(response, REFRESH_TOKEN_COOKIE_NAME);
    }

    public static void clearOAuthRedirectCookie(HttpServletResponse response) {
        clearCookie(response, OAUTH_REDIRECT_COOKIE_NAME);
    }

    private static void addCookie(HttpServletResponse response, String token, String name, long maxAgeMillis) {
        boolean isSecure = checkIsSecure();
        ResponseCookie cookie = ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(maxAgeMillis / 1000)
                .sameSite(isSecure ? "None" : "Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static void clearCookie(HttpServletResponse response, String name) {
        boolean isSecure = checkIsSecure();
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(0)
                .sameSite(isSecure ? "None" : "Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static boolean checkIsSecure() {
        try {
            org.springframework.web.context.request.RequestAttributes attrs = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes) attrs).getRequest();
                String origin = request.getHeader("Origin");
                String xForwardedProto = request.getHeader("X-Forwarded-Proto");
                
                // If it is ngrok HTTPS, or headers indicate HTTPS
                return "https".equalsIgnoreCase(xForwardedProto) 
                        || (origin != null && origin.startsWith("https://"))
                        || request.isSecure();
            }
        } catch (Exception e) {
            // Fallback if request context is not available (e.g. during startup or non-web threads)
        }
        return false;
    }
}
