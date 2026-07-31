package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.auth.*;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserToken;
import com.mgmtp.gives.enums.TokenType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.event.auth.UserRegisteredEvent;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.AuthMapper;
import com.mgmtp.gives.repository.RefreshTokenRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.repository.UserTokenRepository;
import com.mgmtp.gives.security.JwtService;
import com.mgmtp.gives.service.AuthService;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.service.RefreshTokenService;
import com.mgmtp.gives.util.CookieUtils;
import com.mgmtp.gives.util.TokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static com.mgmtp.gives.common.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserTokenRepository userTokenRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final MailProps mailProps;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_DURATION_MINUTES = 15;

    @Override
    @Transactional
    public Void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        log.info("Register request received. email={}", email);

        if (userRepo.existsByEmail(email)) {
            log.warn("Register failed: email already exists. email={}", email);
            throw new AppException(EMAIL_ALREADY_EXISTS);
        }

        User newUser = userRepo.save(User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(UserRole.USER)
                .status(UserStatus.INACTIVE)
                .build());

        User savedUser = userRepo.save(newUser);

        String verificationToken = TokenUtils.generateSecureToken();
        UserToken emailVerificationToken = UserToken.builder()
                .user(savedUser)
                .tokenHash(TokenUtils.hash(verificationToken))
                .type(TokenType.VERIFY_EMAIL)
                .expiresAt(LocalDateTime.now().plus(mailProps.getVerifyExpiration(), ChronoUnit.MILLIS))
                .build();

        userTokenRepo.save(emailVerificationToken);
        eventPublisher.publishEvent(new UserRegisteredEvent(
                        request.email(),
                        request.fullName(),
                        verificationToken
                )
        );

        log.info("Register successful, verification email sent. userId={}, email={}",
                savedUser.getId(), savedUser.getEmail());
        return null;
    }

    @Override
    @Transactional
    public Void verifyEmail(String token) {
        log.info("Email verification request received");

        UserToken emailVerificationToken = userTokenRepo.findByTokenHashAndType(TokenUtils.hash(token), TokenType.VERIFY_EMAIL)
                .orElseThrow(() -> {
                    log.warn("Email verification failed: invalid token");
                    return new AppException(INVALID_TOKEN);
                });

        User currUser = emailVerificationToken.getUser();
        if (emailVerificationToken.getUsedAt() != null) {
            log.warn("Email verification failed: token already used. userId={}, email={}",
                    currUser.getId(), currUser.getEmail());
            throw new AppException(INVALID_TOKEN);
        }

        if (emailVerificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Email verification failed: token expired. userId={}, email={}",
                    currUser.getId(), currUser.getEmail());
            throw new AppException(EXPIRED_TOKEN);
        }

        currUser.setStatus(UserStatus.ACTIVE);
        emailVerificationToken.setUsedAt(LocalDateTime.now());

        userTokenRepo.revokeAllByUserAndType(currUser, TokenType.VERIFY_EMAIL);
        log.info("Email verified successfully. userId={}, email={}",
                currUser.getId(), currUser.getEmail());
        return null;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed: email not found. email={}", email);
                    return new AppException(INVALID_CREDENTIALS);
                });

        if (UserStatus.BANNED.equals(user.getStatus())) {
            log.warn("Login failed: account banned. userId={}, email={}", user.getId(), email);
            throw new AppException(UNAUTHORIZED, "Your account has been banned.");
        }

        if (UserStatus.INACTIVE.equals(user.getStatus())) {
            log.warn("Login failed: account inactive. userId={}, email={}", user.getId(), email);
            throw new AppException(ACCOUNT_INACTIVE, "Your account is not yet activated. Please check your email to activate your account.");
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                log.warn("Login failed: account locked until {}. userId={}, email={}",
                        user.getLockedUntil(), user.getId(), email);
                throw new AppException(ACCOUNT_LOCKED, "Your account has been locked. Please try again later.");
            } else {
                // Lock period has expired – reset the counter
                user.setLockedUntil(null);
                user.setFailedAttemptCount(0);
                userRepo.save(user);
                log.info("Lock expired, failed attempt counter reset. userId={}, email={}", user.getId(), email);
            }
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int newFailCount = user.getFailedAttemptCount() + 1;
            user.setFailedAttemptCount(newFailCount);

            if (newFailCount >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_TIME_DURATION_MINUTES));
                userRepo.save(user);
                log.warn("Login failed: account locked after {} failed attempts. userId={}, email={}",
                        newFailCount, user.getId(), email);
                throw new AppException(ACCOUNT_LOCKED,
                        "Your account has been locked because you entered the wrong password more than 5 times. Please try again in 15 minutes.");
            }

            userRepo.save(user);
            log.warn("Login failed: wrong password. attempt={}/{}, userId={}, email={}",
                    newFailCount, MAX_FAILED_ATTEMPTS, user.getId(), email);
            throw new AppException(INVALID_CREDENTIALS);
        }

        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        userRepo.save(user);

        String accessToken = jwtService.generateAccessToken(authMapper.toTokenGenerationRequest(user));
        String refreshToken = refreshTokenService.generate(authMapper.toTokenGenerationRequest(user));

        log.info("Login successful. userId={}, email={}", user.getId(), email);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    @Transactional
    public Void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("Forgot password failed: email not found. email={}", email);
            throw new ResourceNotFoundException(USER_NOT_FOUND);
        }

        String rawToken = TokenUtils.generateSecureToken();
        String hashedToken = TokenUtils.hash(rawToken);

        userTokenRepo.revokeAllByUserAndType(user, TokenType.RESET_PASSWORD);

        UserToken token = UserToken.builder()
                .user(user)
                .type(TokenType.RESET_PASSWORD)
                .tokenHash(hashedToken)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        userTokenRepo.save(token);

        emailService.sendResetPasswordEmail(user.getEmail(), user.getFullName(), rawToken);
        log.info("Forgot password email sent. userId={}, email={}", user.getId(), user.getEmail());
        return null;

    }

    @Override
    @Transactional
    public Void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new AppException(PASSWORDS_DO_NOT_MATCH);
        }
        String hashedToken = TokenUtils.hash(request.token());

        UserToken token = userTokenRepo.findByTokenHashAndType(hashedToken, TokenType.RESET_PASSWORD)
                .orElseThrow(() -> {
                    log.warn("Reset password failed: invalid token");
                    return new AppException(INVALID_TOKEN, "Invalid token. Please request a new link at "
                            + mailProps.getFrontendUrl() + "/forgot-password");
                });

        if (token.getUsedAt() != null) {
            log.warn("Reset password failed: token already used. userId={}", token.getUser().getId());
            throw new AppException(INVALID_TOKEN, "This token has already been used. Please request a new link at "
                    + mailProps.getFrontendUrl() + "/forgot-password");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            userTokenRepo.revokeAllByUserAndType(token.getUser(), TokenType.RESET_PASSWORD);
            log.warn("Reset password failed: token expired. userId={}", token.getUser().getId());
            throw new AppException(EXPIRED_TOKEN, "This token has expired. Please request a new link at "
                    + mailProps.getFrontendUrl() + "/forgot-password");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepo.save(user);

        refreshTokenRepo.deleteByUserId(user.getId());
        token.setUsedAt(LocalDateTime.now());
        userTokenRepo.revokeAllByUserAndType(user, TokenType.RESET_PASSWORD);

        log.info("Password reset successful, all sessions revoked. userId={}, email={}",
                user.getId(), user.getEmail());
        return null;
    }

    @Override
    @Transactional
    public Void resendActivationEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(UNAUTHORIZED));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AppException(EMAIL_ALREADY_VERIFIED);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new AppException(UNAUTHORIZED, "User has been banned");
        }

        userTokenRepo.revokeAllByUserAndType(user, TokenType.VERIFY_EMAIL);

        String verificationToken = TokenUtils.generateSecureToken();
        UserToken emailVerificationToken = UserToken.builder()
                .user(user)
                .tokenHash(TokenUtils.hash(verificationToken))
                .type(TokenType.VERIFY_EMAIL)
                .expiresAt(LocalDateTime.now().plus(mailProps.getVerifyExpiration(), ChronoUnit.MILLIS))
                .build();

        userTokenRepo.save(emailVerificationToken);

        eventPublisher.publishEvent(new UserRegisteredEvent(
                user.getEmail(),
                user.getFullName(),
                verificationToken
        ));

        log.info("Resend verification email successful. userId={}, email={}", user.getId(), user.getEmail());
        return null;
    }

    @Override
    public UserInfoResponse getCurrentUser(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("Get current user failed: email not found. email={}", normalizedEmail);
                    return new AppException(UNAUTHORIZED);
                });
        return authMapper.toUserInfoResponse(user);
    }

    @Override
    public Void logout(HttpServletRequest request) {
        CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME)
                .ifPresent(refreshTokenService::revoke);
        log.info("Logout processed.");
        return null;
    }

    @Override
    @Transactional
    public UserInfoResponse updateProfile(String email, UpdateProfileRequest request) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(UNAUTHORIZED));

        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        User savedUser = userRepo.save(user);

        log.info("Profile updated successfully for userId={}, email={}", savedUser.getId(), savedUser.getEmail());
        return authMapper.toUserInfoResponse(savedUser);
    }

    @Override
    @Transactional
    public Void changePassword(String email, ChangePasswordRequest request) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(UNAUTHORIZED));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(PASSWORD_INCORRECT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepo.save(user);

        refreshTokenRepo.deleteByUserId(user.getId());
        log.info("Password changed and refresh tokens invalidated for userId={}, email={}", user.getId(), user.getEmail());
        return null;
    }
}
