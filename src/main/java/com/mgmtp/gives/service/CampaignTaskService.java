package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskActivityResponse;
import com.mgmtp.gives.dto.campaign_task.CreateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.TaskAttachmentResponse;
import com.mgmtp.gives.dto.campaign_task.TaskAssignableMemberResponse;
import com.mgmtp.gives.dto.campaign_task.UpdateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.MoveCampaignTaskRequest;
import com.mgmtp.gives.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.mgmtp.gives.enums.TaskStatus;

public interface CampaignTaskService {
    CampaignTaskResponse createTask(Long campaignId, CreateCampaignTaskRequest request, User currentUser);

    CampaignTaskResponse updateTask(Long taskId, UpdateCampaignTaskRequest request, User currentUser);

    CampaignTaskResponse moveTask(Long taskId, MoveCampaignTaskRequest request, User currentUser);

    void deleteTask(Long taskId, User currentUser);

    void permanentlyDeleteArchivedTask(Long taskId, User currentUser);

    CampaignTaskResponse getTaskById(Long taskId, User currentUser);

    Page<CampaignTaskActivityResponse> getTaskActivities(Long taskId, Pageable pageable, User currentUser);

    Page<CampaignTaskResponse> getTasksByCampaign(Long campaignId, TaskStatus status, Long assigneeId,
            Boolean isArchived, Boolean isDeleted, Pageable pageable, User currentUser);

    CampaignTaskResponse archiveTask(Long taskId, User currentUser);

    CampaignTaskResponse unarchiveTask(Long taskId, User currentUser);

    CampaignTaskResponse restoreDeletedTask(Long taskId, User currentUser);

    java.util.List<TaskAssignableMemberResponse> getAssignableMembers(Long campaignId, User currentUser);

    CampaignTaskResponse addAssignee(Long taskId, Long userId, User currentUser);

    CampaignTaskResponse removeAssignee(Long taskId, Long userId, User currentUser);

    CampaignTaskResponse addLabel(Long taskId, Long labelId, User currentUser);

    CampaignTaskResponse removeLabel(Long taskId, Long labelId, User currentUser);

    TaskAttachmentResponse addAttachment(Long taskId, MultipartFile file, User currentUser);

    void removeAttachment(Long taskId, Long attachmentId, User currentUser);
}
