package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.auth.*;

public interface AuthService {
    Void register(RegisterRequest request);

    Void verifyEmail(String token);

    AuthResponse login(LoginRequest request);

    Void resendActivationEmail(String email);

    Void forgotPassword(ForgotPasswordRequest request);

    Void resetPassword(ResetPasswordRequest request);

    UserInfoResponse getCurrentUser(String email);
}
