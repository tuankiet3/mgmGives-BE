package com.mgmtp.gives.service.impl;

import static com.mgmtp.gives.common.ErrorCode.*;

import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.auth.RegisterRequest;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserToken;
import com.mgmtp.gives.enums.TokenType;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.event.UserRegisteredEvent;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.repository.UserTokenRepository;
import com.mgmtp.gives.service.AuthService;
import com.mgmtp.gives.service.EmailService;
import com.mgmtp.gives.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service @RequiredArgsConstructor @Slf4j
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserTokenRepository userTokenRepo;
    private final MailProps mailProps;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    @Override @Transactional
    public Void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        log.info("Register request received. email={}", email);

        User user = userRepo.findByEmail(email).orElse(null);
        User savedUser;

        if (user != null) {
            log.info("Existing user found for email={}, status={}", email, user.getStatus());
            if (user.getStatus() == UserStatus.ACTIVE) {
                log.warn("Register failed: email already active. email={}", email);
                throw new AppException(EMAIL_ALREADY_EXISTS);
            }

            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFullName(request.fullName());
            savedUser = userRepo.save(user);
            userTokenRepo.revokeAllByUserAndType(savedUser, TokenType.VERIFY_EMAIL);
        } else {
             savedUser = userRepo.save(User.builder()
                    .email(request.email())
                    .passwordHash(passwordEncoder.encode(request.password()))
                    .fullName(request.fullName())
                    .role(UserRole.USER)
                    .status(UserStatus.INACTIVE)
                    .build());
        }

        String verificationToken = TokenUtils.generateSecureToken();
        UserToken emailVerificationToken = UserToken.builder()
                .user(savedUser)
                .tokenHash(TokenUtils.hash(verificationToken))
                .type(TokenType.VERIFY_EMAIL)
                .expiresAt(LocalDateTime.now().plus(mailProps.getVerifyExpiration(), ChronoUnit.MILLIS))
                .build();

        userTokenRepo.save(emailVerificationToken);
        eventPublisher.publishEvent(new UserRegisteredEvent(
                                        savedUser.getEmail(),
                                        savedUser.getFullName(),
                                        verificationToken
                )
        );

        log.info("Register successful, verification email sent. userId={}, email={}",
                savedUser.getId(), savedUser.getEmail());
        return null;
    }

    @Override @Transactional
    public Void verifyEmail(String token) {
        log.info("Email verification request received");

        UserToken emailVerificationToken = userTokenRepo.findByTokenHashAndType(TokenUtils.hash(token), TokenType.VERIFY_EMAIL)
                .orElseThrow(() -> {
                    log.warn("Email verification failed: invalid token");
                    throw new AppException(INVALID_TOKEN);
                });

        User currUser = emailVerificationToken.getUser();
        if (emailVerificationToken.getUsedAt() != null) {
            log.warn("Email verification failed: token already used. userId={}, email={}",
                    currUser.getId(), currUser.getEmail());
            throw new AppException(INVALID_TOKEN);
        }

        if (emailVerificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(EXPIRED_TOKEN);
        }

        currUser.setStatus(UserStatus.ACTIVE);
        emailVerificationToken.setUsedAt(LocalDateTime.now());

        userTokenRepo.revokeAllByUserAndType(currUser, TokenType.VERIFY_EMAIL);
        log.info("Email verified successfully. userId={}, email={}",
                currUser.getId(), currUser.getEmail());
        return null;
    }
}
