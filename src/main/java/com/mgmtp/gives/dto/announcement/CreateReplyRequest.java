package com.mgmtp.gives.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateReplyRequest(
        @NotBlank(message = "Reply content cannot be empty")
        @Size(max = 1000, message = "Reply cannot exceed 1000 characters")
        String content,
        @Positive(message = "Referenced reply ID must be positive")
        Long inReplyToReplyId
) {
    public CreateReplyRequest(String content) {
        this(content, null);
    }
}
