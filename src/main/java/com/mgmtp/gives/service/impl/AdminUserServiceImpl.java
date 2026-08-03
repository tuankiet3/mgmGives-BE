package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.user.*;
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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mgmtp.gives.security.CustomUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mgmtp.gives.common.ErrorCode.EMAIL_ALREADY_EXISTS;
import static com.mgmtp.gives.common.ErrorCode.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final List<String> REQUIRED_CSV_HEADERS = List.of("email", "fullName", "password", "role", "status");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

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
    public AdminBulkImportUserResponse importUsersFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "CSV file is required");
        }

        List<AdminBulkImportUserError> errors = new ArrayList<>();
        List<CsvUserRow> csvUsers = readUsersFromCsv(file, errors);

        if (!csvUsers.isEmpty()) {
            addExistingEmailErrors(csvUsers, errors);
        }

        if (!errors.isEmpty()) {
            AdminBulkImportUserResponse result = new AdminBulkImportUserResponse(0, List.of(), errors);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "CSV import validation failed", result);
        }

        if (csvUsers.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "CSV file does not contain user rows");
        }

        long hashStartTime = System.currentTimeMillis();
        List<User> users = csvUsers.parallelStream()
                .map(this::toUser)
                .toList();
        log.info("Imported user passwords hashed: count={}, durationMs={}", users.size(), System.currentTimeMillis() - hashStartTime);

        long saveStartTime = System.currentTimeMillis();
        List<User> savedUsers = userRepository.saveAll(users);
        log.info("Imported users persisted: count={}, durationMs={}", savedUsers.size(), System.currentTimeMillis() - saveStartTime);

        log.info("Users imported successfully by admin: count={}", savedUsers.size());
        return new AdminBulkImportUserResponse(savedUsers.size(), List.of(), List.of());
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

    private List<CsvUserRow> readUsersFromCsv(MultipartFile file, List<AdminBulkImportUserError> errors) {
        List<CsvUserRow> csvUsers = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = csvFormat.parse(reader)) {
            Map<String, Integer> headerIndexes = parseHeaderIndexes(csvParser.getHeaderMap(), errors);
            if (headerIndexes.isEmpty()) {
                errors.add(new AdminBulkImportUserError(1, "header", "CSV header is required"));
                return csvUsers;
            }

            if (!errors.isEmpty()) {
                return csvUsers;
            }

            for (CSVRecord record : csvParser) {
                int rowNumber = Math.toIntExact(record.getRecordNumber() + 1);
                CsvUserRow csvUser = parseUserRow(rowNumber, record, headerIndexes, emailsInFile, errors);
                if (csvUser != null) {
                    csvUsers.add(csvUser);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Import users failed: Unable to read CSV file", ex);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unable to read CSV file");
        }

        return csvUsers;
    }

    private Map<String, Integer> parseHeaderIndexes(Map<String, Integer> headers, List<AdminBulkImportUserError> errors) {
        Map<String, Integer> headerIndexes = new HashMap<>();

        headers.forEach((header, index) -> headerIndexes.put(normalizeHeader(header), index));

        for (String requiredHeader : REQUIRED_CSV_HEADERS) {
            if (!headerIndexes.containsKey(normalizeHeader(requiredHeader))) {
                errors.add(new AdminBulkImportUserError(1, requiredHeader, "CSV header is missing required column: " + requiredHeader));
            }
        }

        return headerIndexes;
    }

    private CsvUserRow parseUserRow(
            int rowNumber,
            CSVRecord record,
            Map<String, Integer> headerIndexes,
            Set<String> emailsInFile,
            List<AdminBulkImportUserError> errors) {
        int errorCountBeforeRow = errors.size();

        String email = normalizeEmail(value(record, headerIndexes, "email"));
        String fullName = value(record, headerIndexes, "fullName").trim();
        String phone = value(record, headerIndexes, "phone").trim();
        String password = value(record, headerIndexes, "password");
        UserRole role = parseRole(rowNumber, value(record, headerIndexes, "role"), errors);
        UserStatus status = parseStatus(rowNumber, value(record, headerIndexes, "status"), errors);

        validateEmail(rowNumber, email, emailsInFile, errors);
        validateFullName(rowNumber, fullName, errors);
        validatePhone(rowNumber, phone, errors);
        validatePassword(rowNumber, password, errors);

        if (errors.size() > errorCountBeforeRow) {
            return null;
        }

        return new CsvUserRow(rowNumber, email, fullName, phone.isBlank() ? null : phone, password, role, status);
    }

    private void addExistingEmailErrors(List<CsvUserRow> csvUsers, List<AdminBulkImportUserError> errors) {
        Set<String> emails = csvUsers.stream()
                .map(CsvUserRow::email)
                .collect(Collectors.toSet());
        Set<String> existingEmails = userRepository.findAllByEmailIn(emails).stream()
                .map(User::getEmail)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        csvUsers.stream()
                .filter(csvUser -> existingEmails.contains(csvUser.email()))
                .forEach(csvUser -> errors.add(new AdminBulkImportUserError(
                        csvUser.rowNumber(),
                        "email",
                        "Email already exists"
                )));
    }

    private User toUser(CsvUserRow csvUser) {
        return User.builder()
                .email(csvUser.email())
                .fullName(csvUser.fullName())
                .phone(csvUser.phone())
                .passwordHash(passwordEncoder.encode(csvUser.password()))
                .role(csvUser.role())
                .status(csvUser.status())
                .failedAttemptCount(0)
                .build();
    }

    private void validateEmail(int rowNumber, String email, Set<String> emailsInFile, List<AdminBulkImportUserError> errors) {
        if (email.isBlank()) {
            errors.add(new AdminBulkImportUserError(rowNumber, "email", "Email is required"));
            return;
        }
        if (email.length() > 255) {
            errors.add(new AdminBulkImportUserError(rowNumber, "email", "Email cannot exceed 255 characters"));
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            errors.add(new AdminBulkImportUserError(rowNumber, "email", "Invalid email format"));
            return;
        }
        if (!emailsInFile.add(email)) {
            errors.add(new AdminBulkImportUserError(rowNumber, "email", "Email is duplicated in CSV file"));
        }
    }

    private void validateFullName(int rowNumber, String fullName, List<AdminBulkImportUserError> errors) {
        if (fullName.isBlank()) {
            errors.add(new AdminBulkImportUserError(rowNumber, "fullName", "Full name is required"));
            return;
        }
        if (fullName.length() < 2 || fullName.length() > 255) {
            errors.add(new AdminBulkImportUserError(rowNumber, "fullName", "Full name must be between 2 and 255 characters"));
        }
    }

    private void validatePhone(int rowNumber, String phone, List<AdminBulkImportUserError> errors) {
        if (phone.length() > 20) {
            errors.add(new AdminBulkImportUserError(rowNumber, "phone", "Phone number cannot exceed 20 characters"));
        }
    }

    private void validatePassword(int rowNumber, String password, List<AdminBulkImportUserError> errors) {
        if (password == null || password.isBlank()) {
            errors.add(new AdminBulkImportUserError(rowNumber, "password", "Password is required"));
            return;
        }
        if (password.length() < 6 || password.length() > 100) {
            errors.add(new AdminBulkImportUserError(rowNumber, "password", "Password must be between 6 and 100 characters"));
        }
    }

    private UserRole parseRole(int rowNumber, String value, List<AdminBulkImportUserError> errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(new AdminBulkImportUserError(rowNumber, "role", "Role is required"));
            return null;
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(new AdminBulkImportUserError(rowNumber, "role", "Role must be one of: " + enumValues(UserRole.values())));
            return null;
        }
    }

    private UserStatus parseStatus(int rowNumber, String value, List<AdminBulkImportUserError> errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(new AdminBulkImportUserError(rowNumber, "status", "Status is required"));
            return null;
        }
        try {
            return UserStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(new AdminBulkImportUserError(rowNumber, "status", "Status must be one of: " + enumValues(UserStatus.values())));
            return null;
        }
    }

    private String value(CSVRecord record, Map<String, Integer> headerIndexes, String header) {
        Integer index = headerIndexes.get(normalizeHeader(header));
        if (index == null || index >= record.size()) {
            return "";
        }
        return record.get(index);
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim().replace("_", "").toLowerCase(Locale.ROOT);
    }

    private String enumValues(Enum<?>[] values) {
        return java.util.Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

}
