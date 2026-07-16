package com.mgmtp.gives.dto.campaign_task;

import com.mgmtp.gives.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CampaignTaskResponse(
                Long id,
                Long campaignId,
                String title,
                String description,
                TaskStatus status,
                long position,
                LocalDateTime dueDate,
                boolean isArchived,
                UserSummary createdBy,
                List<AssigneeInfo> assignees,
                List<LabelInfo> labels,
                List<TaskAttachmentResponse> attachments,
                LocalDateTime createdAt,
                LocalDateTime updatedAt,
                LocalDateTime deletedAt,
                Long version) {
        public record UserSummary(
                        Long id,
                        String fullName,
                        String email,
                        String avatarUrl) {
        }

        public record AssigneeInfo(
                        Long id,
                        String fullName,
                        String email,
                        String avatarUrl) {
        }

        public record LabelInfo(
                        Long id,
                        String name,
                        String color) {
        }
}
