package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.CreateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.TaskAttachmentResponse;
import com.mgmtp.gives.dto.campaign_task.UpdateCampaignTaskRequest;
import com.mgmtp.gives.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.mgmtp.gives.enums.TaskStatus;

public interface CampaignTaskService {
    CampaignTaskResponse createTask(Long campaignId, CreateCampaignTaskRequest request, User currentUser);

    CampaignTaskResponse updateTask(Long taskId, UpdateCampaignTaskRequest request, User currentUser);

    void deleteTask(Long taskId, User currentUser);

    CampaignTaskResponse getTaskById(Long taskId);

    Page<CampaignTaskResponse> getTasksByCampaign(Long campaignId, TaskStatus status, Long assigneeId, Boolean isArchived, Pageable pageable);

    CampaignTaskResponse archiveTask(Long taskId, User currentUser);

    CampaignTaskResponse restoreTask(Long taskId, User currentUser);

    CampaignTaskResponse addAssignee(Long taskId, Long userId, User currentUser);

    CampaignTaskResponse removeAssignee(Long taskId, Long userId, User currentUser);

    CampaignTaskResponse addLabel(Long taskId, Long labelId, User currentUser);

    CampaignTaskResponse removeLabel(Long taskId, Long labelId, User currentUser);

    TaskAttachmentResponse addAttachment(Long taskId, MultipartFile file, User currentUser);

    void removeAttachment(Long taskId, Long attachmentId, User currentUser);
}
