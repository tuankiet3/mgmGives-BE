package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.JwtProps;
import com.mgmtp.gives.dto.auth.*;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.AuthService;
import com.mgmtp.gives.service.RefreshTokenService;
import com.mgmtp.gives.util.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user registration and email verification")
public class AuthController {
    private final AuthService service;
    private final JwtProps jwtProps;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates a user profile and sends a verification email.")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(service.register(request), "PLEASE CHECK YOUR EMAIL TO VERIFY YOUR ACCOUNT");
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        log.info("Login");
        AuthResponse authResponse = service.login(loginRequest);

        CookieUtils.addAccessTokenCookie(response, authResponse.getAccessToken(), jwtProps.getAccessTokenExpiration());
        CookieUtils.addRefreshCookie(response, authResponse.getRefreshToken(), jwtProps.getRefreshTokenExpiration());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify email address", description = "Activates the user account using the token sent via email.")
    public ApiResponse<?> verify(
            @Parameter(description = "The verification token from the email link", required = true) @RequestParam String token) {
        return ApiResponse.success(service.verifyEmail(token), "User verified successfully");
    }

    @PostMapping("/resend-activation")
    @Operation(summary = "Resend verification email", description = "Resends the activation link to the user's registered email address.")
    public ApiResponse<?> resendActivationEmail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) ResendActivationRequest request
    ) {
        String email = (request != null && request.email() != null && !request.email().isBlank())
                ? request.email()
                : (userDetails != null ? userDetails.getUsername() : null);

        if (email == null || email.isBlank()) {
            throw new AppException(com.mgmtp.gives.common.ErrorCode.VALIDATION_ERROR, "Email is required");
        }

        service.resendActivationEmail(email);
        return ApiResponse.success(null, "PLEASE CHECK YOUR EMAIL TO VERIFY YOUR ACCOUNT");
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Retrieves profile details of the currently authenticated user.")
    public ApiResponse<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(service.getCurrentUser(userDetails.getUsername()));
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

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Clears access and refresh token cookies.")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(request);

        CookieUtils.clearAccessTokenCookie(response);
        CookieUtils.clearRefreshTokenCookie(response);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates a new access token using the refresh token from the HttpOnly cookie.")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenService.refresh(request, response);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/profile")
    @Operation(summary = "Update profile details", description = "Updates full name and phone of the currently authenticated user.")
    public ApiResponse<UserInfoResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(service.updateProfile(userDetails.getUsername(), request),
                "Profile updated successfully");
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change password", description = "Changes the password of the currently authenticated user.")
    public ApiResponse<?> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(userDetails.getUsername(), request);
        return ApiResponse.success(null, "Password changed successfully");
    }
}
