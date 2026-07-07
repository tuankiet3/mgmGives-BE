package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.user.AdminBulkImportUserResponse;
import com.mgmtp.gives.dto.user.AdminCreateUserRequest;
import com.mgmtp.gives.dto.user.AdminUpdateUserRequest;
import com.mgmtp.gives.dto.user.AdminUserResponse;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin User Management", description = "Endpoints for administrators to manage users and their roles/statuses")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves a paginated list of users, optionally filtered by roles, statuses, or a search keyword.")
    public ApiResponse<?> getAllUsers(
            @Parameter(description = "Optional list of roles to filter by", example = "ADMIN,USER")
            @RequestParam(required = false) List<UserRole> roles,
            @Parameter(description = "Optional list of statuses to filter by", example = "ACTIVE,INACTIVE")
            @RequestParam(required = false) List<UserStatus> statuses,
            @Parameter(description = "Optional search query to filter users by name, email, or phone", example = "john")
            @RequestParam(required = false) String search,
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<AdminUserResponse> userPage = adminUserService.getAllUsers(roles, statuses, search, pageable);
        PageResponse<AdminUserResponse> pageResponse = PageResponse.of(userPage, userPage.getContent());
        return ApiResponse.success(pageResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves details of a single user by their ID.")
    public ApiResponse<?> getUserById(
            @Parameter(description = "The ID of the user", required = true, example = "1")
            @PathVariable Long id) {
        AdminUserResponse user = adminUserService.getUserById(id);
        return ApiResponse.success(user);
    }

    @PostMapping
    @Operation(summary = "Create a new user", description = "Creates a new user with the specified details, role, and status.")
    public ApiResponse<?> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        AdminUserResponse user = adminUserService.createUser(request);
        return ApiResponse.success(user, "User Created Successfully");
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import users from CSV",
            description = "Creates users from a CSV file with headers: email,fullName,phone,password,role,status."
    )
    public ApiResponse<?> importUsersFromCsv(
            @Parameter(description = "CSV file containing users to create", required = true)
            @RequestParam("file") MultipartFile file) {
        AdminBulkImportUserResponse result = adminUserService.importUsersFromCsv(file);
        return ApiResponse.success(result, "Users Imported Successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates details, role, or status of an existing user by their ID.")
    public ApiResponse<?> updateUser(
            @Parameter(description = "The ID of the user to update", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        AdminUserResponse user = adminUserService.updateUser(id, request);
        return ApiResponse.success(user);
    }

}
