package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.user.AdminCreateUserRequest;
import com.mgmtp.gives.dto.user.AdminUpdateUserRequest;
import com.mgmtp.gives.dto.user.AdminUserResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.mapper.UserMapper;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.AdminUserService;
import com.mgmtp.gives.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mgmtp.gives.security.CustomUserDetails;

import java.util.List;

import static com.mgmtp.gives.common.ErrorCode.EMAIL_ALREADY_EXISTS;
import static com.mgmtp.gives.common.ErrorCode.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(List<UserRole> roles, List<UserStatus> statuses, String search, Pageable pageable) {
        Specification<User> spec = null;

        if (roles != null && !roles.isEmpty()) {
            spec = UserSpecifications.hasRoleIn(roles);
        }

        if (statuses != null && !statuses.isEmpty()) {
            spec = (spec == null) ? UserSpecifications.hasStatusIn(statuses) : spec.and(UserSpecifications.hasStatusIn(statuses));
        }

        if (search != null && !search.trim().isEmpty()) {
            spec = (spec == null) ? UserSpecifications.matchesKeyword(search) : spec.and(UserSpecifications.matchesKeyword(search));
        }

        Page<User> users = userRepository.findAll(spec, pageable);
        return users.map(userMapper::toAdminUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Get user failed: User not found. id={}", id);
                    return new AppException(USER_NOT_FOUND);
                });
        return userMapper.toAdminUserResponse(user);
    }

    @Override
    @Transactional
    public AdminUserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Create user failed: Email already exists. email={}", request.email());
            throw new AppException(EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email().trim().toLowerCase())
                .fullName(request.fullName().trim())
                .phone(request.phone() != null ? request.phone().trim() : null)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(request.status())
                .failedAttemptCount(0)
                .build();

        User saved = userRepository.save(user);
        log.info("User created successfully by admin: id={}, email={}", saved.getId(), saved.getEmail());
        return userMapper.toAdminUserResponse(saved);
    }

    @Override
    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update user failed: User not found. id={}", id);
                    return new AppException(USER_NOT_FOUND);
                });

        // Prevent admin from banning themselves
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            User currentUser = userDetails.getUser();
            if (currentUser.getId().equals(id) && request.status() == UserStatus.BANNED) {
                log.warn("Update user failed: Admin cannot ban themselves. id={}", id);
                throw new AppException(ErrorCode.VALIDATION_ERROR, "You cannot ban yourself.");
            }
        }

        userMapper.updateEntityFromRequest(request, user);
        
        if (request.password() != null && !request.password().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        user.setFullName(user.getFullName().trim());
        if (user.getPhone() != null) {
            user.setPhone(user.getPhone().trim());
        }

        User saved = userRepository.save(user);
        log.info("User updated successfully by admin: id={}, email={}", saved.getId(), saved.getEmail());
        return userMapper.toAdminUserResponse(saved);
    }
}
