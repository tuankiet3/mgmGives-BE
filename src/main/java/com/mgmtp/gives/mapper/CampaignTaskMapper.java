package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskActivityResponse;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.TaskAttachmentResponse;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.CampaignTaskActivity;
import com.mgmtp.gives.entity.TaskAttachment;
import com.mgmtp.gives.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CampaignTaskMapper {

    public CampaignTaskResponse toResponse(CampaignTask task) {
        User creator = task.getCreatedBy();
        CampaignTaskResponse.UserSummary createdBy = creator == null ? null
                : new CampaignTaskResponse.UserSummary(
                        creator.getId(),
                        creator.getFullName(),
                        creator.getEmail(),
                        creator.getAvatarUrl());

        List<CampaignTaskResponse.AssigneeInfo> assignees = task.getAssignments().stream()
                .map(assignment -> new CampaignTaskResponse.AssigneeInfo(
                        assignment.getUser().getId(),
                        assignment.getUser().getFullName(),
                        assignment.getUser().getEmail(),
                        assignment.getUser().getAvatarUrl()))
                .toList();

        List<CampaignTaskResponse.LabelInfo> labels = task.getLabels().stream()
                .map(label -> new CampaignTaskResponse.LabelInfo(
                        label.getId(),
                        label.getName(),
                        label.getColor()))
                .toList();

        List<TaskAttachmentResponse> attachments = task.getAttachments().stream()
                .map(this::toAttachmentResponse)
                .toList();

        return new CampaignTaskResponse(
                task.getId(),
                task.getCampaign() == null ? null : task.getCampaign().getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPosition(),
                task.getDueDate(),
                task.isArchived(),
                createdBy,
                assignees,
                labels,
                attachments,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getDeletedAt(),
                task.getVersion());
    }

    public CampaignTaskActivityResponse toActivityResponse(CampaignTaskActivity activity) {
        User actor = activity.getActor();
        return new CampaignTaskActivityResponse(
                activity.getId(),
                activity.getAction(),
                new CampaignTaskActivityResponse.ActorSummary(
                        actor == null ? null : actor.getId(),
                        activity.getActorName(),
                        actor == null ? null : actor.getAvatarUrl()),
                activity.getDetails(),
                activity.getCreatedAt());
    }

    public TaskAttachmentResponse toAttachmentResponse(TaskAttachment attachment) {
        User uploader = attachment.getUploadedBy();
        TaskAttachmentResponse.UploadedByInfo uploadedBy = uploader == null ? null
                : new TaskAttachmentResponse.UploadedByInfo(
                        uploader.getId(),
                        uploader.getFullName(),
                        uploader.getEmail(),
                        uploader.getAvatarUrl());
        return new TaskAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getStoredFilename(),
                attachment.getFileType(),
                attachment.getFileSize(),
                uploadedBy,
                attachment.getUploadedAt());
    }
}
