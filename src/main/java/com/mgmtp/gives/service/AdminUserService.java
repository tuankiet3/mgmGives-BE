package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.user.AdminCreateUserRequest;
import com.mgmtp.gives.dto.user.AdminBulkImportUserResponse;
import com.mgmtp.gives.dto.user.AdminUpdateUserRequest;
import com.mgmtp.gives.dto.user.AdminUserResponse;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminUserService {
    Page<AdminUserResponse> getAllUsers(List<UserRole> roles, List<UserStatus> statuses, String search, Pageable pageable);

    AdminUserResponse getUserById(Long id);

    AdminUserResponse createUser(AdminCreateUserRequest request);

    AdminBulkImportUserResponse importUsersFromCsv(MultipartFile file);

    AdminUserResponse updateUser(Long id, AdminUpdateUserRequest request);
}
