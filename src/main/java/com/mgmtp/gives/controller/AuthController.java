package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.auth.RegisterRequest;
import com.mgmtp.gives.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration and email verification")
public class AuthController {
    private final AuthService service;

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates a user profile and sends a verification email.")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(service.register(request), "PLEASE CHECK YOUR EMAIL TO VERIFY YOUR ACCOUNT");
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify email address", description = "Activates the user account using the token sent via email.")
    public ApiResponse<?> verify(
            @Parameter(description = "The verification token from the email link", required = true)
            @RequestParam String token) {
        return ApiResponse.success(service.verifyEmail(token), "User verified successfully");
    }
}
