package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.auth.RegisterRequest;

public interface AuthService {
    Void register(RegisterRequest request);
    Void verifyEmail(String token);
}
