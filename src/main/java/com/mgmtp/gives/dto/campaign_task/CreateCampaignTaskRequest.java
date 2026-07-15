package com.mgmtp.gives.dto.campaign_task;

import com.mgmtp.gives.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateCampaignTaskRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        LocalDateTime dueDate,

        List<Long> assigneeIds,

        List<Long> labelIds,

        TaskStatus status
) {
}
