package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.auth.ForgotPasswordRequest;
import com.mgmtp.gives.dto.auth.RegisterRequest;
import com.mgmtp.gives.dto.auth.ResetPasswordRequest;
import com.mgmtp.gives.dto.auth.LoginRequest;
import com.mgmtp.gives.dto.auth.AuthResponse;
import com.mgmtp.gives.service.AuthService;
import com.mgmtp.gives.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.mgmtp.gives.common.JwtProps;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and email verification")
public class AuthController {
    private final AuthService service;
    private final JwtProps jwtProps;

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates a user profile and sends a verification email.")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(service.register(request), "PLEASE CHECK YOUR EMAIL TO VERIFY YOUR ACCOUNT");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = service.login(loginRequest);

        ResponseCookie accessCookie = ResponseCookie.from("access_token", response.getAccessToken())
                .httpOnly(true)
                .secure(false) // in production this should be true if using HTTPS
                .path("/")
                .maxAge(jwtProps.getAccessTokenExpiration() / 1000)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", response.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(jwtProps.getRefreshTokenExpiration() / 1000)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(null, "Login successful"));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify email address", description = "Activates the user account using the token sent via email.")
    public ApiResponse<?> verify(
            @Parameter(description = "The verification token from the email link", required = true) @RequestParam String token) {
        return ApiResponse.success(service.verifyEmail(token), "User verified successfully");
    }

    @PostMapping("/resend-activation")
    @Operation(summary = "Resend verification email", description = "Resends the activation link to the user's registered email address.")
    public ApiResponse<?> resendActivationEmail(@AuthenticationPrincipal CustomUserDetails userDetails) {
        service.resendActivationEmail(userDetails.getUsername());
        return ApiResponse.success(null, "PLEASE CHECK YOUR EMAIL TO VERIFY YOUR ACCOUNT");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Generates a password reset token and sends an email to the user if verified.")
    public ApiResponse<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ApiResponse.success(service.forgotPassword(request), "Please check your email to reset your password");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Allows password reset using the token sent in the email.")
    public ApiResponse<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(service.resetPassword(request), "Password has been reset successfully");
    }
}
