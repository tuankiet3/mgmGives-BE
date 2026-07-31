package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskChangeAction;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskChangedPayload;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskActivityResponse;
import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.CreateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.MoveCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.TaskAttachmentResponse;
import com.mgmtp.gives.dto.campaign_task.TaskAssignableMemberResponse;
import com.mgmtp.gives.dto.campaign_task.UpdateCampaignTaskRequest;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.*;
import com.mgmtp.gives.enums.CampaignTaskActivityAction;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.event.notification.TaskAssignedEvent;
import com.mgmtp.gives.event.notification.TaskCreatedEmailEvent;
import com.mgmtp.gives.event.notification.TaskDescriptionUpdatedEvent;
import com.mgmtp.gives.event.notification.TaskStatusChangedEvent;
import com.mgmtp.gives.event.notification.TaskUnassignedEvent;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.event.task.CampaignTaskChangedEvent;
import com.mgmtp.gives.event.task.CampaignTaskConflictEvent;
import com.mgmtp.gives.repository.*;
import com.mgmtp.gives.service.CampaignTaskService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.specification.CampaignTaskSpecifications;
import com.mgmtp.gives.util.CampaignAccessHelper;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final CampaignTaskActivityRepository campaignTaskActivityRepository;
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
        List<User> assignees = resolveAndValidateAssignees(campaignId, request.assigneeIds());

        // Validate labels
        Set<CampaignTaskLabel> labels = resolveAndValidateLabels(campaignId, request.labelIds());

        TaskStatus initialStatus = request.status() == null ? TaskStatus.TODO : request.status();
        CampaignTask savedTask = CampaignTask.builder()
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

        CampaignTaskResponse response = saveAndPublish(
                savedTask,
                CampaignTaskChangeAction.CREATED,
                currentUser,
                activity(CampaignTaskActivityAction.TASK_CREATED,
                        details("status", initialStatus.name())));

        log.info("Task created: campaignId={}, taskId={}, userId={}",
                campaignId, savedTask.getId(), currentUser.getId());

        if (savedTask.getAssignments() != null && !savedTask.getAssignments().isEmpty()) {
            Set<NotificationRecipient> assigneesRecipients = savedTask.getAssignments().stream()
                    .map(assignment -> new NotificationRecipient(assignment.getUser().getId(),
                            assignment.getUser().getEmail()))
                    .collect(Collectors.toSet());

            // Publish event for in-app notification
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    campaignId,
                    savedTask.getId(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    assigneesRecipients));

            // Publish event for email notification
            eventPublisher.publishEvent(new TaskCreatedEmailEvent(
                    campaignId,
                    campaign.getTitle(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    savedTask.getDueDate(),
                    assigneesRecipients));
        }

        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse updateTask(Long taskId, UpdateCampaignTaskRequest request, User currentUser) {
        CampaignTask savedTask = findTask(taskId);
        Long campaignId = savedTask.getCampaign().getId();

        TaskStatus previousStatus = savedTask.getStatus();
        String previousTitle = savedTask.getTitle();
        String previousDescription = savedTask.getDescription();
        LocalDateTime previousDueDate = savedTask.getDueDate();
        Map<Long, String> previousAssignees = assignmentNames(savedTask);
        Map<Long, String> previousLabels = labelNames(savedTask);

        boolean isAdmin = validateCanModifyTask(savedTask, currentUser);
        if (!isAdmin && (request.assigneeIds() != null || request.labelIds() != null)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        }
        if (request.version() != null && !Objects.equals(request.version(), savedTask.getVersion())) {
            throwTaskUpdateConflict(savedTask, currentUser);
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Task title cannot be blank");
        }

        TaskStatus oldStatus = savedTask.getStatus();
        boolean descriptionChanged = request.description() != null
                && !Objects.equals(request.description(), savedTask.getDescription());

        List<User> requestedAssignees = request.assigneeIds() == null
                ? null
                : resolveAndValidateAssignees(campaignId, request.assigneeIds());
        Set<CampaignTaskLabel> requestedLabels = request.labelIds() == null
                ? null
                : resolveAndValidateLabels(campaignId, request.labelIds());
        boolean statusChanged = request.status() != null && request.status() != savedTask.getStatus();

        // Status update: Admin OR Assignee allowed
        if (statusChanged) {
            if (savedTask.isArchived()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Archived tasks cannot be moved");
            }
            validateCanReopenCompletedTask(savedTask, request.status(), isAdmin);
            savedTask.setStatus(request.status());
            savedTask.setPosition(nextActivePosition(campaignId, request.status()));
        }

        // Info update: Admin OR Assignee allowed
        if (Boolean.TRUE.equals(request.clearDueDate()) && request.dueDate() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Due date cannot be set and cleared at the same time");
        }
        if (request.title() != null || request.description() != null
                || request.dueDate() != null || Boolean.TRUE.equals(request.clearDueDate())) {
            if (request.title() != null) {
                savedTask.setTitle(request.title());
            }
            if (request.description() != null) {
                savedTask.setDescription(request.description());
            }
            if (Boolean.TRUE.equals(request.clearDueDate())) {
                savedTask.setDueDate(null);
            } else if (request.dueDate() != null) {
                validateDueDate(request.dueDate(), savedTask.getCampaign());
                savedTask.setDueDate(request.dueDate());
            }
        }
        if (requestedAssignees != null) {
            syncTaskAssignments(savedTask, requestedAssignees);
        }
        if (requestedLabels != null) {
            savedTask.getLabels().clear();
            savedTask.getLabels().addAll(requestedLabels);
        }

        List<ActivityDraft> activities = collectUpdateActivities(
                savedTask,
                previousStatus,
                previousTitle,
                previousDescription,
                previousDueDate,
                previousAssignees,
                previousLabels);
        savedTask.setUpdatedAt(LocalDateTime.now());
        CampaignTaskResponse response = saveAndPublish(
                savedTask,
                statusChanged ? CampaignTaskChangeAction.MOVED : CampaignTaskChangeAction.UPDATED,
                currentUser,
                activities.toArray(ActivityDraft[]::new));

        log.info("Task updated: taskId={}, userId={}", taskId, currentUser.getId());

        Set<NotificationRecipient> newlyAssignedRecipients = savedTask.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(Objects::nonNull)
                .filter(user -> !previousAssignees.containsKey(user.getId()))
                .map(user -> new NotificationRecipient(user.getId(), user.getEmail()))
                .collect(Collectors.toSet());
        if (!newlyAssignedRecipients.isEmpty()) {
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    campaignId,
                    savedTask.getId(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    newlyAssignedRecipients));
            eventPublisher.publishEvent(new TaskCreatedEmailEvent(
                    campaignId,
                    savedTask.getCampaign().getTitle(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    savedTask.getDueDate(),
                    newlyAssignedRecipients));
        }

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
                        savedTask.getCampaign().getUser().getEmail()));
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
                        recipients));
            }
        } else {
            // Regular update (Title, Description, etc. changed without status change)
            if (descriptionChanged && savedTask.getAssignments() != null && !savedTask.getAssignments().isEmpty()) {
                Set<NotificationRecipient> assigneesRecipients = savedTask.getAssignments().stream()
                        .map(assignment -> new NotificationRecipient(assignment.getUser().getId(),
                                assignment.getUser().getEmail()))
                        .collect(Collectors.toSet());

                // Exclude the current user (action user)
                assigneesRecipients.removeIf(r -> r.userId().equals(currentUser.getId()));

                if (!assigneesRecipients.isEmpty()) {
                    eventPublisher.publishEvent(new TaskDescriptionUpdatedEvent(
                            campaignId,
                            savedTask.getId(),
                            savedTask.getTitle(),
                            assigneesRecipients));
                }
            }
        }
        return response;
    }

    @Override
    @Transactional
    public CampaignTaskResponse moveTask(Long taskId, MoveCampaignTaskRequest request, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();

        boolean isAdmin = validateCanModifyTask(task, currentUser);
        if (task.isArchived()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Archived tasks cannot be moved");
        }
        validateCanReopenCompletedTask(task, request.status(), isAdmin);

        if (!Objects.equals(task.getVersion(), request.expectedVersion())) {
            throwTaskUpdateConflict(task, currentUser);
        }
        TaskStatus previousStatus = task.getStatus();
        if (request.position() != null) {
            campaignTaskRepository.shiftPositionsAfter(
                    campaignId, request.status(), request.position(), taskId);
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        int updatedRows = campaignTaskRepository.moveIfVersionMatches(
                taskId, request.status().name(), request.expectedVersion(), request.position(), updatedAt);
        if (updatedRows == 0) {
            CampaignTask currentTask = findTask(taskId);
            throwTaskUpdateConflict(currentTask, currentUser);
        }

        CampaignTask movedTask = findTask(taskId);
        CampaignTaskResponse response = toResponse(movedTask);
        if (previousStatus != movedTask.getStatus()) {
            recordActivities(
                    movedTask,
                    currentUser,
                    List.of(activity(
                            CampaignTaskActivityAction.STATUS_CHANGED,
                            details("fromStatus", previousStatus.name(), "toStatus", movedTask.getStatus().name()))));
        }
        publishTaskChange(movedTask, response, CampaignTaskChangeAction.MOVED, currentUser);

        log.info("Task moved: taskId={}, status={}, position={}, version={}, userId={}",
                taskId, response.status(), response.position(), response.version(), currentUser.getId());
        return response;
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser,
                ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        boolean changed = task.getDeletedAt() == null;
        task.setDeletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        saveAndPublish(
                task,
                CampaignTaskChangeAction.DELETED,
                currentUser,
                changed
                        ? activity(CampaignTaskActivityAction.TASK_DELETED, Map.of())
                        : null);
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

        Long campaignId = task.getCampaign().getId();
        Set<String> recipients = resolveTaskUpdateRecipients(task);
        Long tombstoneVersion = task.getVersion() == null ? 1L : task.getVersion() + 1;
        LocalDateTime deletedAt = LocalDateTime.now();
        task.getAttachments().forEach(attachment -> mediaService.softDeleteTaskFile(attachment.getStoredFilename()));
        campaignTaskRepository.delete(task);
        campaignTaskRepository.flush();
        publishTaskTombstone(campaignId, taskId, tombstoneVersion, deletedAt, currentUser, recipients);
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
    public Page<CampaignTaskActivityResponse> getTaskActivities(
            Long taskId,
            Pageable pageable,
            User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignMemberOrAdmin(
                task.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        return campaignTaskActivityRepository.findByTaskId(taskId, pageable)
                .map(this::toActivityResponse);
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
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser,
                ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        boolean changed = !task.isArchived();
        task.setArchived(true);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTaskResponse response = saveAndPublish(
                task,
                CampaignTaskChangeAction.ARCHIVED,
                currentUser,
                changed ? activity(CampaignTaskActivityAction.TASK_ARCHIVED, Map.of()) : null);
        log.info("Task archived: taskId={}, userId={}", taskId, currentUser.getId());
        return response;
    }

    @Override
    @Transactional
    public CampaignTaskResponse unarchiveTask(Long taskId, User currentUser) {
        CampaignTask task = findTask(taskId);
        campaignAccessHelper.validateCampaignAdmin(task.getCampaign().getId(), currentUser,
                ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        boolean changed = task.isArchived();
        task.setPosition(nextActivePosition(task.getCampaign().getId(), task.getStatus()));
        task.setArchived(false);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTaskResponse response = saveAndPublish(
                task,
                CampaignTaskChangeAction.UNARCHIVED,
                currentUser,
                changed ? activity(CampaignTaskActivityAction.TASK_UNARCHIVED, Map.of()) : null);
        log.info("Task unarchived: taskId={}, userId={}", taskId, currentUser.getId());
        return response;
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
        return saveAndPublish(
                task,
                CampaignTaskChangeAction.RESTORED,
                currentUser,
                activity(CampaignTaskActivityAction.TASK_RESTORED, Map.of()));
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
        CampaignTask savedTask = findTask(taskId);
        Long campaignId = savedTask.getCampaign().getId();
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
                .task(savedTask)
                .user(assignee)
                .assignedAt(LocalDateTime.now())
                .build();
        savedTask.getAssignments().add(assignment);
        savedTask.setUpdatedAt(LocalDateTime.now());

        CampaignTaskResponse response = saveAndPublish(
                savedTask,
                CampaignTaskChangeAction.UPDATED,
                currentUser,
                activity(
                        CampaignTaskActivityAction.ASSIGNEE_ADDED,
                        details("userId", assignee.getId(), "name", displayName(assignee))));
        log.info("Assignee added: taskId={}, userId={}, addedBy={}", taskId, userId, currentUser.getId());

        if (assignee != null) {
            Set<NotificationRecipient> recipients = Set
                    .of(new NotificationRecipient(assignee.getId(), assignee.getEmail()));
            eventPublisher.publishEvent(new TaskAssignedEvent(
                    campaignId,
                    savedTask.getId(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    recipients));

            // Publish event for email notification to the newly added assignee
            eventPublisher.publishEvent(new TaskCreatedEmailEvent(
                    campaignId,
                    savedTask.getCampaign().getTitle(),
                    savedTask.getTitle(),
                    savedTask.getDescription(),
                    savedTask.getDueDate(),
                    recipients));
        }

        return toResponse(savedTask);
    }

    @Override
    @Transactional
    public CampaignTaskResponse removeAssignee(Long taskId, Long userId, User currentUser) {
        CampaignTask savedTask = findTask(taskId);
        Long campaignId = savedTask.getCampaign().getId();
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        User unassignedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String removedAssigneeName = savedTask.getAssignments().stream()
                .filter(assignment -> Objects.equals(assignment.getUser().getId(), userId))
                .map(assignment -> displayName(assignment.getUser()))
                .findFirst()
                .orElse(null);
        boolean removed = savedTask.getAssignments().removeIf(a -> Objects.equals(a.getUser().getId(), userId));

        if (!removed) {
            throw new ResourceNotFoundException(ErrorCode.ASSIGNEE_NOT_FOUND);
        }

        savedTask.setUpdatedAt(LocalDateTime.now());

        CampaignTaskResponse response = saveAndPublish(
                savedTask,
                CampaignTaskChangeAction.UPDATED,
                currentUser,
                activity(
                        CampaignTaskActivityAction.ASSIGNEE_REMOVED,
                        details("userId", userId, "name", removedAssigneeName)));
        log.info("Assignee removed: taskId={}, userId={}, removedBy={}", taskId, userId, currentUser.getId());

        // Publish event to notify unassigned user
        eventPublisher.publishEvent(new TaskUnassignedEvent(
                campaignId,
                savedTask.getId(),
                savedTask.getTitle(),
                new NotificationRecipient(unassignedUser.getId(), unassignedUser.getEmail())));

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

        boolean added = task.getLabels().add(label);
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTaskResponse response = saveAndPublish(
                task,
                CampaignTaskChangeAction.UPDATED,
                currentUser,
                added
                        ? activity(
                                CampaignTaskActivityAction.LABEL_ADDED,
                                details("labelId", label.getId(), "name", label.getName()))
                        : null);
        log.info("Label added: taskId={}, labelId={}, addedBy={}", taskId, labelId, currentUser.getId());
        return response;
    }

    @Override
    @Transactional
    public CampaignTaskResponse removeLabel(Long taskId, Long labelId, User currentUser) {
        CampaignTask task = findTask(taskId);
        Long campaignId = task.getCampaign().getId();
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_TASK_ACCESS);

        String removedLabelName = task.getLabels().stream()
                .filter(label -> Objects.equals(label.getId(), labelId))
                .map(CampaignTaskLabel::getName)
                .findFirst()
                .orElse(null);
        boolean removed = task.getLabels().removeIf(l -> Objects.equals(l.getId(), labelId));
        task.setUpdatedAt(LocalDateTime.now());

        CampaignTaskResponse response = saveAndPublish(
                task,
                CampaignTaskChangeAction.UPDATED,
                currentUser,
                removed
                        ? activity(
                                CampaignTaskActivityAction.LABEL_REMOVED,
                                details("labelId", labelId, "name", removedLabelName))
                        : null);
        log.info("Label removed: taskId={}, labelId={}, removedBy={}", taskId, labelId, currentUser.getId());
        return response;
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

    private List<User> resolveAndValidateAssignees(Long campaignId, List<Long> assigneeIds) {
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
        validateCanModifyTask(task, currentUser);

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

        task.getAttachments().add(attachment);
        task.setUpdatedAt(LocalDateTime.now());
        CampaignTask savedTask = saveTaskOrThrowConflict(task, currentUser);
        recordActivities(
                savedTask,
                currentUser,
                List.of(activity(
                        CampaignTaskActivityAction.ATTACHMENT_ADDED,
                        details(
                                "attachmentId", attachment.getId(),
                                "name", attachment.getOriginalFilename()))));
        CampaignTaskResponse response = toResponse(savedTask);
        publishTaskChange(savedTask, response, CampaignTaskChangeAction.UPDATED, currentUser);
        log.info("Attachment added: taskId={}, attachmentId={}, uploadedBy={}",
                taskId, attachment.getId(), currentUser.getId());
        return toAttachmentResponse(attachment);
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

        task.getAttachments().removeIf(item -> Objects.equals(item.getId(), attachmentId));
        mediaService.softDeleteTaskFile(attachment.getStoredFilename());
        task.setUpdatedAt(LocalDateTime.now());
        CampaignTask savedTask = saveTaskOrThrowConflict(task, currentUser);
        recordActivities(
                savedTask,
                currentUser,
                List.of(activity(
                        CampaignTaskActivityAction.ATTACHMENT_REMOVED,
                        details(
                                "attachmentId", attachmentId,
                                "name", attachment.getOriginalFilename()))));
        CampaignTaskResponse response = toResponse(savedTask);
        publishTaskChange(savedTask, response, CampaignTaskChangeAction.UPDATED, currentUser);
        log.info("Attachment removed: taskId={}, attachmentId={}, removedBy={}", taskId, attachmentId,
                currentUser.getId());
    }

    private boolean validateCanModifyTask(CampaignTask task, User currentUser) {
        Long campaignId = task.getCampaign().getId();
        boolean isAdmin = campaignAccessHelper.isCampaignAdmin(campaignId, currentUser);
        boolean isAssignee = taskAssignmentRepository.existsByTaskIdAndUserId(
                task.getId(), currentUser.getId())
                && campaignAccessHelper.isCampaignMember(campaignId, currentUser.getId());
        if (!isAdmin && !isAssignee) {
            throw new AppException(ErrorCode.UNAUTHORIZED_TASK_ACCESS);
        }
        return isAdmin;
    }

    private void validateCanReopenCompletedTask(
            CampaignTask task,
            TaskStatus targetStatus,
            boolean isAdmin) {
        if (task.getStatus() == TaskStatus.DONE && targetStatus != TaskStatus.DONE && !isAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED_TASK_STATUS_UPDATE);
        }
    }

    private CampaignTaskResponse saveAndPublish(
            CampaignTask task,
            CampaignTaskChangeAction action,
            User currentUser,
            ActivityDraft... activities) {
        CampaignTask savedTask = saveTaskOrThrowConflict(task, currentUser);
        recordActivities(savedTask, currentUser, Arrays.asList(activities));
        CampaignTaskResponse response = toResponse(savedTask);
        publishTaskChange(savedTask, response, action, currentUser);
        return response;
    }

    private List<ActivityDraft> collectUpdateActivities(
            CampaignTask task,
            TaskStatus previousStatus,
            String previousTitle,
            String previousDescription,
            LocalDateTime previousDueDate,
            Map<Long, String> previousAssignees,
            Map<Long, String> previousLabels) {
        List<ActivityDraft> activities = new ArrayList<>();

        if (previousStatus != task.getStatus()) {
            activities.add(activity(
                    CampaignTaskActivityAction.STATUS_CHANGED,
                    details("fromStatus", previousStatus.name(), "toStatus", task.getStatus().name())));
        }
        if (!Objects.equals(previousTitle, task.getTitle())) {
            activities.add(activity(
                    CampaignTaskActivityAction.TITLE_UPDATED,
                    details("fromTitle", previousTitle, "toTitle", task.getTitle())));
        }
        if (!Objects.equals(previousDescription, task.getDescription())) {
            activities.add(activity(CampaignTaskActivityAction.DESCRIPTION_UPDATED, Map.of()));
        }
        if (!Objects.equals(previousDueDate, task.getDueDate())) {
            activities.add(activity(
                    CampaignTaskActivityAction.DUE_DATE_UPDATED,
                    details(
                            "fromDueDate", toActivityValue(previousDueDate),
                            "toDueDate", toActivityValue(task.getDueDate()))));
        }

        Map<Long, String> currentAssignees = assignmentNames(task);
        previousAssignees.forEach((userId, name) -> {
            if (!currentAssignees.containsKey(userId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.ASSIGNEE_REMOVED,
                        details("userId", userId, "name", name)));
            }
        });
        currentAssignees.forEach((userId, name) -> {
            if (!previousAssignees.containsKey(userId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.ASSIGNEE_ADDED,
                        details("userId", userId, "name", name)));
            }
        });

        Map<Long, String> currentLabels = labelNames(task);
        previousLabels.forEach((labelId, name) -> {
            if (!currentLabels.containsKey(labelId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.LABEL_REMOVED,
                        details("labelId", labelId, "name", name)));
            }
        });
        currentLabels.forEach((labelId, name) -> {
            if (!previousLabels.containsKey(labelId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.LABEL_ADDED,
                        details("labelId", labelId, "name", name)));
            }
        });

        return activities;
    }

    private void recordActivities(
            CampaignTask task,
            User actor,
            Collection<ActivityDraft> activities) {
        List<CampaignTaskActivity> entities = new ArrayList<>();
        for (ActivityDraft draft : activities) {
            if (draft == null) {
                continue;
            }
            entities.add(CampaignTaskActivity.builder()
                    .task(task)
                    .action(draft.action())
                    .actor(actor)
                    .actorName(displayName(actor))
                    .details(draft.details())
                    .build());
        }
        if (!entities.isEmpty()) {
            campaignTaskActivityRepository.saveAll(entities);
        }
    }

    private CampaignTaskActivityResponse toActivityResponse(CampaignTaskActivity activity) {
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

    private Map<Long, String> assignmentNames(CampaignTask task) {
        Map<Long, String> names = new LinkedHashMap<>();
        task.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(User::getId))
                .forEach(user -> names.put(user.getId(), displayName(user)));
        return names;
    }

    private Map<Long, String> labelNames(CampaignTask task) {
        Map<Long, String> names = new LinkedHashMap<>();
        task.getLabels().stream()
                .sorted(Comparator.comparing(CampaignTaskLabel::getId))
                .forEach(label -> names.put(label.getId(), label.getName()));
        return names;
    }

    private String displayName(User user) {
        if (user == null) {
            return "Unknown user";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail() == null || user.getEmail().isBlank() ? "Unknown user" : user.getEmail();
    }

    private String toActivityValue(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private ActivityDraft activity(
            CampaignTaskActivityAction action,
            Map<String, Object> details) {
        return new ActivityDraft(action, details);
    }

    private Map<String, Object> details(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            details.put((String) keyValues[index], keyValues[index + 1]);
        }
        return details;
    }

    private record ActivityDraft(
            CampaignTaskActivityAction action,
            Map<String, Object> details) {
    }

    private CampaignTask saveTaskOrThrowConflict(CampaignTask task, User currentUser) {
        try {
            CampaignTask savedTask = campaignTaskRepository.save(task);
            campaignTaskRepository.flush();
            return savedTask;
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
            eventPublisher.publishEvent(new CampaignTaskConflictEvent(task.getId(), currentUser, null));
            throw new AppException(ErrorCode.RESOURCE_UPDATE_CONFLICT);
        }
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

    private Set<String> resolveTaskUpdateRecipients(CampaignTask task) {
        Set<String> recipientEmails = new HashSet<>();
        campaignMemberRepository.findMemberRecipientsByCampaignId(task.getCampaign().getId()).stream()
                .map(NotificationRecipient::email)
                .filter(Objects::nonNull)
                .forEach(recipientEmails::add);
        User owner = task.getCampaign().getUser();
        if (owner != null && owner.getEmail() != null) {
            recipientEmails.add(owner.getEmail());
        }
        return Set.copyOf(recipientEmails);
    }

    private void publishTaskChange(
            CampaignTask task,
            CampaignTaskResponse response,
            CampaignTaskChangeAction action,
            User currentUser) {
        CampaignTaskChangedPayload payload = taskChangedPayload(response, action, currentUser);
        eventPublisher.publishEvent(new CampaignTaskChangedEvent(payload, resolveTaskUpdateRecipients(task)));
    }

    private void throwTaskUpdateConflict(CampaignTask task, User currentUser) {
        CampaignTaskResponse response = toResponse(task);
        CampaignTaskChangedPayload payload = taskChangedPayload(
                response,
                CampaignTaskChangeAction.UPDATED,
                currentUser);
        eventPublisher.publishEvent(new CampaignTaskConflictEvent(task.getId(), currentUser, payload));
        throw new AppException(ErrorCode.RESOURCE_UPDATE_CONFLICT, response);
    }

    private CampaignTaskChangedPayload taskChangedPayload(
            CampaignTaskResponse response,
            CampaignTaskChangeAction action,
            User currentUser) {
        return new CampaignTaskChangedPayload(
                "TASK_CHANGED",
                action,
                response.campaignId(),
                response.id(),
                response.version(),
                response,
                response.updatedAt(),
                currentUser.getId());
    }

    private void publishTaskTombstone(
            Long campaignId,
            Long taskId,
            Long version,
            LocalDateTime updatedAt,
            User currentUser,
            Set<String> recipients) {
        CampaignTaskChangedPayload payload = new CampaignTaskChangedPayload(
                "TASK_CHANGED",
                CampaignTaskChangeAction.PERMANENTLY_DELETED,
                campaignId,
                taskId,
                version,
                null,
                updatedAt,
                currentUser.getId());
        eventPublisher.publishEvent(new CampaignTaskChangedEvent(payload, recipients));
    }

    private void syncTaskAssignments(CampaignTask task, List<User> assignees) {
        Set<Long> requestedUserIds = assignees.stream()
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
        task.getAssignments().removeIf(assignment -> !requestedUserIds.contains(assignment.getUser().getId()));

        Set<Long> existingUserIds = task.getAssignments().stream()
                .map(assignment -> assignment.getUser().getId())
                .collect(java.util.stream.Collectors.toSet());
        LocalDateTime assignedAt = LocalDateTime.now();
        assignees.stream()
                .filter(user -> !existingUserIds.contains(user.getId()))
                .map(user -> TaskAssignment.builder()
                        .task(task)
                        .user(user)
                        .assignedAt(assignedAt)
                        .build())
                .forEach(task.getAssignments()::add);
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
