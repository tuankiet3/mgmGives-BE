package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.CreateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.TaskAttachmentResponse;
import com.mgmtp.gives.dto.campaign_task.TaskAssignableMemberResponse;
import com.mgmtp.gives.dto.campaign_task.UpdateCampaignTaskRequest;
import com.mgmtp.gives.entity.*;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.*;
import com.mgmtp.gives.service.CampaignTaskService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.specification.CampaignTaskSpecifications;
import com.mgmtp.gives.util.CampaignAccessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.event.notification.TaskAssignedEvent;
import com.mgmtp.gives.event.notification.TaskStatusChangedEvent;
import com.mgmtp.gives.event.notification.TaskDescriptionUpdatedEvent;
import com.mgmtp.gives.event.notification.TaskUnassignedEvent;
import com.mgmtp.gives.event.notification.TaskCreatedEmailEvent;
import com.mgmtp.gives.enums.CampaignMemberRole;
import org.springframework.context.ApplicationEventPublisher;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignTaskServiceImpl implements CampaignTaskService {

    private final CampaignTaskRepository campaignTaskRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignLabelRepository campaignLabelRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final UserRepository userRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final CampaignAccessHelper campaignAccessHelper;
    private final MediaService mediaService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CampaignTaskResponse createTask(Long campaignId, CreateCampaignTaskRequest request, User currentUser) {
        Campaign campaign = campaignAccessHelper.findCampaignOrThrow(campaignId);
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        // Validate due date
        validateDueDate(request.dueDate(), campaign);

        // Validate assignees
        List<User> assignees = resolveAndValidateAssignees(campaignId, request.assigneeIds(), currentUser);

        // Validate labels
        Set<CampaignTaskLabel> labels = resolveAndValidateLabels(campaignId, request.labelIds());

        TaskStatus initialStatus = request.status() == null ? TaskStatus.TODO : request.status();
        CampaignTask task = CampaignTask.builder()
                .campaign(campaign)
                .title(request.title())
                .description(request.description())
                .status(initialStatus)
                .position(nextActivePosition(campaignId, initialStatus))
                .dueDate(request.dueDate())
                .version(0L)
                .createdBy(currentUser)
                .updatedAt(LocalDateTime.now())
                .build();

        CampaignTask savedTask = campaignTaskRepository.save(task);

        // Create assignments
        Set<TaskAssignment> assignments = new HashSet<>();
        for (User assignee : assignees) {
            TaskAssignment assignment = TaskAssignment.builder()
                    .task(savedTask)
                    .user(assignee)
                    .assignedAt(LocalDateTime.now())
                    .build();
            assignments.add(assignment);
        }
        savedTask.setAssignments(assignments);
        savedTask.setLabels(labels);

        savedTask = campaignTaskRepository.save(savedTask);

        log.info("Task created: campaignId={}, taskId={}, userId={}",
                campaignId, savedTask.getId(), currentUser.getId());

        if (savedTask.getAssignments() != null && !savedTask.getAssignments().isEmpty()) {
            Set<NotificationRecipient> assigneesRecipients = savedTask.getAssignments().stream()
                    .map(assignment -> new NotificationRecipient(assignment.getUser().getId(), assignment.getUser().getEmail()))
                    .collect(Collectors.toSet());

            // Publish event for in-app notification
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    campaignId,
                    savedTask.getId(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    assigneesRecipients
            ));

            // Publish event for email notification
            eventPublisher.publishEvent(new TaskCreatedEmailEvent(
                    campaignId,
                    campaign.getTitle(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    savedTask.getDueDate(),
                    assigneesRecipients
            ));
        }

        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse updateTask(Long taskId, UpdateCampaignTaskRequest request, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();

        boolean isAdmin = campaignAccessHelper.isCampaignAdmin(campaignId, currentUser);
        boolean isAssignee = taskAssignmentRepository.existsByTaskIdAndUserId(taskId, currentUser.getId())
                && campaignAccessHelper.isCampaignMember(campaignId, currentUser.getId());

        if (!isAdmin && !isAssignee) {
            throw new AppException(ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        }
        if (request.version() != null && !Objects.equals(request.version(), task.getVersion())) {
            throw new AppException(ErrorCode.RESOURCE_UPDATE_CONFLICT);
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Task title cannot be blank");
        }

        TaskStatus oldStatus = task.getStatus();
        boolean descriptionChanged = request.description() != null
                && !Objects.equals(request.description(), task.getDescription());

        // Status update: Admin OR Assignee allowed
        if (request.status() != null && request.status() != task.getStatus()) {
            task.setStatus(request.status());
            task.setPosition(nextActivePosition(campaignId, request.status()));
        }

        // Info update: Admin OR Assignee allowed
        if (Boolean.TRUE.equals(request.clearDueDate()) && request.dueDate() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Due date cannot be set and cleared at the same time");
        }
        if (request.title() != null || request.description() != null
                || request.dueDate() != null || Boolean.TRUE.equals(request.clearDueDate())) {
            if (request.title() != null) {
                task.setTitle(request.title());
            }
            if (request.description() != null) {
                task.setDescription(request.description());
            }
            if (Boolean.TRUE.equals(request.clearDueDate())) {
                task.setDueDate(null);
            } else if (request.dueDate() != null) {
                validateDueDate(request.dueDate(), task.getCampaign());
                task.setDueDate(request.dueDate());
            }
        }

        task.setUpdatedAt(LocalDateTime.now());
        CampaignTask savedTask = campaignTaskRepository.save(task);

        log.info("Task updated: taskId={}, userId={}", taskId, currentUser.getId());

        // Check if status changed
        if (request.status() != null && request.status() != oldStatus) {
            Set<NotificationRecipient> recipients = new HashSet<>();

            // Add all current assignees
            if (savedTask.getAssignments() != null) {
                savedTask.getAssignments().stream()
                        .map(a -> new NotificationRecipient(a.getUser().getId(), a.getUser().getEmail()))
                        .forEach(recipients::add);
            }

            // Add Campaign Owner
            if (savedTask.getCampaign().getUser() != null) {
                recipients.add(new NotificationRecipient(
                        savedTask.getCampaign().getUser().getId(),
                        savedTask.getCampaign().getUser().getEmail()
                ));
            }

            // Add Campaign Admins
            List<NotificationRecipient> campaignAdmins = campaignMemberRepository
                    .findRecipientsByCampaignIdAndRole(campaignId, CampaignMemberRole.CAMPAIGN_ADMIN);
            if (campaignAdmins != null) {
                recipients.addAll(campaignAdmins);
            }

            // Exclude the current user (action user)
            recipients.removeIf(r -> r.userId().equals(currentUser.getId()));

            if (!recipients.isEmpty()) {
                eventPublisher.publishEvent(new TaskStatusChangedEvent(
                        campaignId,
                        savedTask.getId(),
                        savedTask.getTitle(),
                        oldStatus,
                        savedTask.getStatus(),
                        recipients
                ));
            }
        } else {
            // Regular update (Title, Description, etc. changed without status change)
            if (descriptionChanged && savedTask.getAssignments() != null && !savedTask.getAssignments().isEmpty()) {
                Set<NotificationRecipient> assigneesRecipients = savedTask.getAssignments().stream()
                        .map(assignment -> new NotificationRecipient(assignment.getUser().getId(), assignment.getUser().getEmail()))
                        .collect(Collectors.toSet());

                // Exclude the current user (action user)
                assigneesRecipients.removeIf(r -> r.userId().equals(currentUser.getId()));

                if (!assigneesRecipients.isEmpty()) {
                    eventPublisher.publishEvent(new TaskDescriptionUpdatedEvent(
                            campaignId,
                            savedTask.getId(),
                            savedTask.getTitle(),
                            assigneesRecipients
                    ));
                }
            }
        }

        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser,
                ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        task.setDeletedAt(LocalDateTime.now());
        campaignTaskRepository.save(task);
        log.info("Task deleted (soft): taskId={}, userId={}", taskId, currentUser.getId());
    }

    @Override
    @Transactional
    public void permanentlyDeleteArchivedTask(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser,
                ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        if (!task.isArchived()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only archived tasks can be permanently deleted");
        }

        task.getAttachments().forEach(attachment -> mediaService.softDeleteTaskFile(attachment.getStoredFilename()));
        campaignTaskRepository.delete(task);
        log.info("Task permanently deleted: taskId={}, userId={}", taskId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignTaskResponse getTaskById(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignMemberOrAdmin(
                task.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        return toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CampaignTaskResponse> getTasksByCampaign(Long campaignId, TaskStatus status, Long assigneeId,
            Boolean isArchived, Boolean isDeleted, Pageable pageable, User currentUser) {
        campaignAccessHelper.findCampaignOrThrow(campaignId);
        if (Boolean.TRUE.equals(isArchived) || Boolean.TRUE.equals(isDeleted)) {
            campaignAccessHelper.validateCampaignAdmin(
                    campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        } else {
            campaignAccessHelper.validateCampaignMemberOrAdmin(
                    campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        }

        Specification<CampaignTask> spec = Specification.where(CampaignTaskSpecifications.hasCampaignId(campaignId))
                .and(CampaignTaskSpecifications.hasStatus(status))
                .and(CampaignTaskSpecifications.hasAssigneeId(assigneeId));

        if (Boolean.TRUE.equals(isDeleted)) {
            spec = spec.and(CampaignTaskSpecifications.isDeleted());
        } else {
            spec = spec.and(CampaignTaskSpecifications.hasIsArchived(isArchived))
                    .and(CampaignTaskSpecifications.isNotDeleted());
        }

        return campaignTaskRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public CampaignTaskResponse archiveTask(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        task.setArchived(true);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTask savedTask = campaignTaskRepository.save(task);
        log.info("Task archived: taskId={}, userId={}", taskId, currentUser.getId());
        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse unarchiveTask(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        task.setPosition(nextActivePosition(task.getCampaign().getId(), task.getStatus()));
        task.setArchived(false);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTask savedTask = campaignTaskRepository.save(task);
        log.info("Task unarchived: taskId={}, userId={}", taskId, currentUser.getId());
        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse restoreDeletedTask(Long taskId, User currentUser) {
        CampaignTask task = findTaskIncludingDeleted(taskId);
        campaignAccessHelper.validateCampaignAdmin(
                task.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        if (task.getDeletedAt() == null) {
            throw new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND);
        }
        task.setDeletedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        return toResponse(campaignTaskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAssignableMemberResponse> getAssignableMembers(Long campaignId, User currentUser) {
        campaignAccessHelper.findCampaignOrThrow(campaignId);
        campaignAccessHelper.validateCampaignAdmin(
                campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        return campaignMemberRepository.findByCampaignId(campaignId).stream()
                .filter(member -> member.getUser() != null)
                .filter(member -> member.getUser().getStatus() == UserStatus.ACTIVE)
                .map(member -> new TaskAssignableMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getFullName(),
                        member.getUser().getEmail(),
                        member.getUser().getAvatarUrl(),
                        member.getRoleInCampaign()))
                .sorted(Comparator.comparing(TaskAssignableMemberResponse::fullName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Override
    @Transactional
    public CampaignTaskResponse addAssignee(Long taskId, Long userId, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        if (taskAssignmentRepository.existsByTaskIdAndUserId(taskId, userId)) {
            throw new AppException(ErrorCode.TASK_ALREADY_ASSIGNED);
        }

        if (!campaignAccessHelper.isCampaignMember(campaignId, userId)) {
            throw new AppException(ErrorCode.INVALID_TASK_ASSIGNEES);
        }

        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (assignee.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_TASK_ASSIGNEES);
        }

        TaskAssignment assignment = TaskAssignment.builder()
                .task(task)
                .user(assignee)
                .assignedAt(LocalDateTime.now())
                .build();
        task.getAssignments().add(assignment);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTask savedTask = campaignTaskRepository.save(task);
        log.info("Assignee added: taskId={}, userId={}, addedBy={}", taskId, userId, currentUser.getId());

        if (assignee != null) {
            Set<NotificationRecipient> recipients = Set.of(new NotificationRecipient(assignee.getId(), assignee.getEmail()));
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    campaignId,
                    savedTask.getId(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    recipients
            ));

            // Publish event for email notification to the newly added assignee
            eventPublisher.publishEvent(new TaskCreatedEmailEvent(
                    campaignId,
                    task.getCampaign().getTitle(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    savedTask.getDueDate(),
                    recipients
            ));
        }

        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse removeAssignee(Long taskId, Long userId, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        User unassignedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        boolean removed = task.getAssignments().removeIf(a -> Objects.equals(a.getUser().getId(), userId));

        if (!removed) {
            throw new ResourceNotFoundException(ErrorCode.ASSIGNEE_NOT_FOUND);
        }

        task.setUpdatedAt(LocalDateTime.now());

        CampaignTask savedTask = campaignTaskRepository.save(task);
        log.info("Assignee removed: taskId={}, userId={}, removedBy={}", taskId, userId, currentUser.getId());

        // Publish event to notify unassigned user
        eventPublisher.publishEvent(new TaskUnassignedEvent(
                campaignId,
                savedTask.getId(),
                savedTask.getTitle(),
                new NotificationRecipient(unassignedUser.getId(), unassignedUser.getEmail())
        ));

        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse addLabel(Long taskId, Long labelId, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        // Check label belongs to this campaign
        CampaignTaskLabel label = campaignLabelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LABEL_NOT_FOUND));
        if (!Objects.equals(label.getCampaign().getId(), campaignId)) {
            throw new AppException(ErrorCode.INVALID_TASK_LABELS);
        }

        task.getLabels().add(label);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTask savedTask = campaignTaskRepository.save(task);
        log.info("Label added: taskId={}, labelId={}, addedBy={}", taskId, labelId, currentUser.getId());
        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse removeLabel(Long taskId, Long labelId, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        task.getLabels().removeIf(l -> Objects.equals(l.getId(), labelId));
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTask savedTask = campaignTaskRepository.save(task);
        log.info("Label removed: taskId={}, labelId={}, removedBy={}", taskId, labelId, currentUser.getId());
        return toResponse(savedTask);
    }

    private CampaignTask findTask(Long taskId) {
        CampaignTask task = findTaskIncludingDeleted(taskId);
        if (task.getDeletedAt() != null) {
            throw new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private CampaignTask findTaskIncludingDeleted(Long taskId) {
        return campaignTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND));
    }

    private void validateDueDate(LocalDateTime dueDate, Campaign campaign) {
        if (dueDate == null)
            return;

        LocalDate dueDay = dueDate.toLocalDate();
        if (dueDay.isBefore(LocalDate.now())) {
            throw new AppException(ErrorCode.TASK_DUE_DATE_IN_PAST);
        }

        if (campaign.getStartDate() != null
                && dueDay.isBefore(campaign.getStartDate().toLocalDate())) {
            throw new AppException(ErrorCode.TASK_DUE_DATE_BEFORE_CAMPAIGN_START);
        }

        if (campaign.getEndDate() != null
                && dueDay.isAfter(campaign.getEndDate().toLocalDate())) {
            throw new AppException(ErrorCode.TASK_DUE_DATE_AFTER_CAMPAIGN_END);
        }
    }

    private List<User> resolveAndValidateAssignees(Long campaignId, List<Long> assigneeIds, User currentUser) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }

        List<Long> uniqueAssigneeIds = new ArrayList<>(new LinkedHashSet<>(assigneeIds));

        // Check all assignees are campaign members
        List<User> assignees = new ArrayList<>();
        for (Long userId : uniqueAssigneeIds) {
            if (!campaignAccessHelper.isCampaignMember(campaignId, userId)) {
                throw new AppException(ErrorCode.INVALID_TASK_ASSIGNEES);
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new AppException(ErrorCode.INVALID_TASK_ASSIGNEES);
            }
            assignees.add(user);
        }
        return assignees;
    }

    private Set<CampaignTaskLabel> resolveAndValidateLabels(Long campaignId, List<Long> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Long> uniqueLabelIds = new ArrayList<>(new LinkedHashSet<>(labelIds));
        List<CampaignTaskLabel> labels = campaignLabelRepository.findByIdInAndCampaignId(uniqueLabelIds, campaignId);
        if (labels.size() != uniqueLabelIds.size()) {
            throw new AppException(ErrorCode.INVALID_TASK_LABELS);
        }
        return new HashSet<>(labels);
    }

    @Override
    @Transactional
    public TaskAttachmentResponse addAttachment(Long taskId, MultipartFile file, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();

        boolean isAdmin = campaignAccessHelper.isCampaignAdmin(campaignId, currentUser);
        boolean isAssignee = taskAssignmentRepository.existsByTaskIdAndUserId(taskId, currentUser.getId());

        if (!isAdmin && !isAssignee) {
            throw new AppException(ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        }

        String storedFilename = mediaService.uploadTaskFile(file);

        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedBy(currentUser)
                .uploadedAt(LocalDateTime.now())
                .build();

        TaskAttachment saved = taskAttachmentRepository.save(attachment);
        log.info("Attachment added: taskId={}, attachmentId={}, uploadedBy={}", taskId, saved.getId(), currentUser.getId());
        return toAttachmentResponse(saved);
    }

    @Override
    @Transactional
    public void removeAttachment(Long taskId, Long attachmentId, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();

        TaskAttachment attachment = taskAttachmentRepository.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.MEDIA_NOT_FOUND));

        boolean isAdmin = campaignAccessHelper.isCampaignAdmin(campaignId, currentUser);
        boolean isUploader = attachment.getUploadedBy().getId().equals(currentUser.getId());

        if (!isAdmin && !isUploader) {
            throw new AppException(ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        }

        taskAttachmentRepository.delete(attachment);
        mediaService.softDeleteTaskFile(attachment.getStoredFilename());
        log.info("Attachment removed: taskId={}, attachmentId={}, removedBy={}", taskId, attachmentId, currentUser.getId());
    }

    private CampaignTaskResponse toResponse(CampaignTask task) {
        User creator = task.getCreatedBy();
        CampaignTaskResponse.UserSummary createdBy = creator == null ? null
                : new CampaignTaskResponse.UserSummary(
                        creator.getId(),
                        creator.getFullName(),
                        creator.getEmail(),
                        creator.getAvatarUrl());

        List<CampaignTaskResponse.AssigneeInfo> assignees = task.getAssignments().stream()
                .map(a -> new CampaignTaskResponse.AssigneeInfo(
                        a.getUser().getId(),
                        a.getUser().getFullName(),
                        a.getUser().getEmail(),
                        a.getUser().getAvatarUrl()))
                .toList();

        List<CampaignTaskResponse.LabelInfo> labels = task.getLabels().stream()
                .map(l -> new CampaignTaskResponse.LabelInfo(
                        l.getId(),
                        l.getName(),
                        l.getColor()))
                .toList();

        List<TaskAttachmentResponse> attachments = task.getAttachments().stream()
                .map(this::toAttachmentResponse)
                .toList();

        return new CampaignTaskResponse(
                task.getId(),
                task.getCampaign() != null ? task.getCampaign().getId() : null,
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

    private long nextActivePosition(Long campaignId, TaskStatus status) {
        campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new AppException(ErrorCode.CAMPAIGN_NOT_FOUND));
        return campaignTaskRepository.findMaxActivePositionByCampaignIdAndStatus(campaignId, status) + 1;
    }

    private TaskAttachmentResponse toAttachmentResponse(TaskAttachment attachment) {
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
