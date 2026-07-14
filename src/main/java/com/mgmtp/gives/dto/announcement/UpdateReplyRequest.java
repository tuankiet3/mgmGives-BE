package com.mgmtp.gives.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateReplyRequest(
        @NotBlank(message = "Reply content cannot be empty")
        @Size(max = 1000, message = "Reply cannot exceed 1000 characters")
        String content
) {
}
