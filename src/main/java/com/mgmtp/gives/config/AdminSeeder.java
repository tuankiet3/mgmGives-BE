package com.mgmtp.gives.config;

import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin.email}")
    private String adminEmail;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Value("${app.seed.admin.full-name:Admin}")
    private String adminFullName;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin user already exists, skipping seed.");
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName(adminFullName)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .failedAttemptCount(0)
                .build();

        userRepository.save(admin);
        log.info("Default admin user seeded successfully: {}", adminEmail);
    }
}
