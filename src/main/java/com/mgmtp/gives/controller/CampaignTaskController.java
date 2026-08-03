package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskActivityResponse;
import com.mgmtp.gives.dto.campaign_task.CreateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.TaskAttachmentResponse;
import com.mgmtp.gives.dto.campaign_task.TaskAssignableMemberResponse;
import com.mgmtp.gives.dto.campaign_task.UpdateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.MoveCampaignTaskRequest;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Campaign Tasks", description = "Endpoints for campaign task management")
public class CampaignTaskController {

        private final CampaignTaskService campaignTaskService;

        @PostMapping("/campaigns/{campaignId}/tasks")
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create a new task for a campaign")
        public ApiResponse<CampaignTaskResponse> createTask(
                        @PathVariable Long campaignId,
                        @Valid @RequestBody CreateCampaignTaskRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to create task: campaignId={}, userId={}",
                                campaignId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.createTask(campaignId, request, userDetails.getUser()),
                                "Task created successfully");
        }

        @GetMapping("/campaigns/{campaignId}/tasks")
        @Operation(summary = "Get all tasks for a campaign")
        public ApiResponse<Page<CampaignTaskResponse>> getTasks(
                        @PathVariable Long campaignId,
                        @RequestParam(required = false) TaskStatus status,
                        @RequestParam(required = false) Long assigneeId,
                        @RequestParam(defaultValue = "false") Boolean isArchived,
                        @RequestParam(defaultValue = "false") Boolean isDeleted,
                        @ParameterObject @PageableDefault(page = 0, size = 10) @SortDefault.SortDefaults({
                                        @SortDefault(sort = "position", direction = Sort.Direction.ASC),
                                        @SortDefault(sort = "id", direction = Sort.Direction.ASC)
                        }) Pageable pageable,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to get tasks: campaignId={}, status={}, assigneeId={}, isArchived={}",
                                campaignId, status, assigneeId, isArchived);
                return ApiResponse.success(
                                campaignTaskService.getTasksByCampaign(campaignId, status, assigneeId, isArchived,
                                                isDeleted, pageable, userDetails.getUser()));
        }

        @GetMapping("/tasks/{taskId}")
        @Operation(summary = "Get task detail by ID")
        public ApiResponse<CampaignTaskResponse> getTask(
                        @PathVariable Long taskId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to get task: taskId={}", taskId);
                return ApiResponse.success(campaignTaskService.getTaskById(taskId, userDetails.getUser()));
        }

        @GetMapping("/tasks/{taskId}/activities")
        @Operation(summary = "Get task activity history")
        public ApiResponse<Page<CampaignTaskActivityResponse>> getTaskActivities(
                        @PathVariable Long taskId,
                        @ParameterObject @PageableDefault(page = 0, size = 20, sort = { "createdAt",
                                        "id" }, direction = Sort.Direction.DESC) Pageable pageable,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ApiResponse.success(
                                campaignTaskService.getTaskActivities(taskId, pageable, userDetails.getUser()));
        }

        @PatchMapping("/tasks/{taskId}")
        @Operation(summary = "Update task info (title, description, dueDate, assignees, labels, status)")
        public ApiResponse<CampaignTaskResponse> updateTask(
                        @PathVariable Long taskId,
                        @Valid @RequestBody UpdateCampaignTaskRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to update task: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.updateTask(taskId, request, userDetails.getUser()),
                                "Task updated successfully");
        }

        @PatchMapping("/tasks/{taskId}/move")
        @Operation(summary = "Move a task to another board column")
        public ApiResponse<CampaignTaskResponse> moveTask(
                        @PathVariable Long taskId,
                        @Valid @RequestBody MoveCampaignTaskRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to move task: taskId={}, status={}, userId={}",
                                taskId, request.status(), userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.moveTask(taskId, request, userDetails.getUser()),
                                "Task moved successfully");
        }

        @DeleteMapping("/tasks/{taskId}")
        @Operation(summary = "Delete a task")
        public ApiResponse<Void> deleteTask(
                        @PathVariable Long taskId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to delete task: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                campaignTaskService.deleteTask(taskId, userDetails.getUser());
                return ApiResponse.success(null, "Task deleted successfully");
        }

        @DeleteMapping("/tasks/{taskId}/permanently")
        @Operation(summary = "Permanently delete an archived task")
        public ApiResponse<Void> permanentlyDeleteArchivedTask(
                        @PathVariable Long taskId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to permanently delete archived task: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                campaignTaskService.permanentlyDeleteArchivedTask(taskId, userDetails.getUser());
                return ApiResponse.success(null, "Task permanently deleted successfully");
        }

        @PostMapping("/tasks/{taskId}/archive")
        @Operation(summary = "Archive a task")
        public ApiResponse<CampaignTaskResponse> archiveTask(
                        @PathVariable Long taskId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to archive task: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.archiveTask(taskId, userDetails.getUser()),
                                "Task archived successfully");
        }

        @PostMapping("/tasks/{taskId}/unarchive")
        @Operation(summary = "Unarchive a task")
        public ApiResponse<CampaignTaskResponse> unarchiveTask(
                        @PathVariable Long taskId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to unarchive task: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.unarchiveTask(taskId, userDetails.getUser()),
                                "Task unarchived successfully");
        }

        @PostMapping("/tasks/{taskId}/restore")
        @Operation(summary = "Restore a soft-deleted task")
        public ApiResponse<CampaignTaskResponse> restoreDeletedTask(
                        @PathVariable Long taskId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to restore deleted task: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.restoreDeletedTask(taskId, userDetails.getUser()),
                                "Task restored successfully");
        }

        @GetMapping("/campaigns/{campaignId}/task-assignees")
        @Operation(summary = "Get campaign members eligible for task assignment")
        public ApiResponse<List<TaskAssignableMemberResponse>> getAssignableMembers(
                        @PathVariable Long campaignId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ApiResponse.success(campaignTaskService.getAssignableMembers(campaignId, userDetails.getUser()));
        }

        @PostMapping("/tasks/{taskId}/assignees/{userId}")
        @Operation(summary = "Add an assignee to a task")
        public ApiResponse<CampaignTaskResponse> addAssignee(
                        @PathVariable Long taskId,
                        @PathVariable Long userId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to add assignee: taskId={}, userId={}, addedBy={}",
                                taskId, userId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.addAssignee(taskId, userId, userDetails.getUser()),
                                "Assignee added successfully");
        }

        @DeleteMapping("/tasks/{taskId}/assignees/{userId}")
        @Operation(summary = "Remove an assignee from a task")
        public ApiResponse<CampaignTaskResponse> removeAssignee(
                        @PathVariable Long taskId,
                        @PathVariable Long userId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to remove assignee: taskId={}, userId={}, removedBy={}",
                                taskId, userId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.removeAssignee(taskId, userId, userDetails.getUser()),
                                "Assignee removed successfully");
        }

        @PostMapping("/tasks/{taskId}/labels/{labelId}")
        @Operation(summary = "Add a label to a task")
        public ApiResponse<CampaignTaskResponse> addLabel(
                        @PathVariable Long taskId,
                        @PathVariable Long labelId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to add label: taskId={}, labelId={}, addedBy={}",
                                taskId, labelId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.addLabel(taskId, labelId, userDetails.getUser()),
                                "Label added successfully");
        }

        @DeleteMapping("/tasks/{taskId}/labels/{labelId}")
        @Operation(summary = "Remove a label from a task")
        public ApiResponse<CampaignTaskResponse> removeLabel(
                        @PathVariable Long taskId,
                        @PathVariable Long labelId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to remove label: taskId={}, labelId={}, removedBy={}",
                                taskId, labelId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.removeLabel(taskId, labelId, userDetails.getUser()),
                                "Label removed successfully");
        }

        @PostMapping(value = "/tasks/{taskId}/attachments", consumes = MULTIPART_FORM_DATA_VALUE)
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Upload an attachment to a task")
        public ApiResponse<TaskAttachmentResponse> uploadAttachment(
                        @PathVariable Long taskId,
                        @RequestParam("file") MultipartFile file,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to upload attachment: taskId={}, userId={}",
                                taskId, userDetails.getUser().getId());
                return ApiResponse.success(
                                campaignTaskService.addAttachment(taskId, file, userDetails.getUser()),
                                "Attachment uploaded successfully");
        }

        @DeleteMapping("/tasks/{taskId}/attachments/{attachmentId}")
        @Operation(summary = "Delete an attachment from a task")
        public ApiResponse<Void> deleteAttachment(
                        @PathVariable Long taskId,
                        @PathVariable Long attachmentId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to delete attachment: taskId={}, attachmentId={}, userId={}",
                                taskId, attachmentId, userDetails.getUser().getId());
                campaignTaskService.removeAttachment(taskId, attachmentId, userDetails.getUser());
                return ApiResponse.success(null, "Attachment deleted successfully");
        }
}
