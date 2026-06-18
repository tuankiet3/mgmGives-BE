package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.auth.ForgotPasswordRequest;
import com.mgmtp.gives.dto.auth.RegisterRequest;
import com.mgmtp.gives.dto.auth.LoginRequest;
import com.mgmtp.gives.dto.auth.AuthResponse;
import com.mgmtp.gives.dto.auth.ResetPasswordRequest;
import com.mgmtp.gives.dto.auth.UserInfoResponse;

public interface AuthService {
    Void register(RegisterRequest request);

    Void verifyEmail(String token);

    AuthResponse login(LoginRequest request);

    Void resendActivationEmail(String email);

    Void forgotPassword(ForgotPasswordRequest request);

    Void resetPassword(ResetPasswordRequest request);
    UserInfoResponse getCurrentUser(String email);
}
