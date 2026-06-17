package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.auth.RegisterRequest;
import com.mgmtp.gives.dto.auth.LoginRequest;
import com.mgmtp.gives.dto.auth.AuthResponse;

public interface AuthService {
    Void register(RegisterRequest request);
    Void verifyEmail(String token);
    AuthResponse login(LoginRequest request);

}
