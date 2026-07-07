package com.mgmtp.gives.dto.user;

import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;

public record CsvUserRow(
        int rowNumber,
        String email,
        String fullName,
        String phone,
        String password,
        UserRole role,
        UserStatus status
) {
}
