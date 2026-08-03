package com.mgmtp.gives.service.support;

import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.CampaignTaskActivity;
import com.mgmtp.gives.entity.CampaignTaskLabel;
import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignTaskActivityAction;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.repository.CampaignTaskActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CampaignTaskActivityTracker {

    private final CampaignTaskActivityRepository campaignTaskActivityRepository;

    public Snapshot snapshot(CampaignTask task) {
        return new Snapshot(
                task.getStatus(),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                assignmentNames(task),
                labelNames(task));
    }

    public List<Draft> collectChanges(Snapshot previous, CampaignTask current) {
        List<Draft> activities = new ArrayList<>();

        if (previous.status() != current.getStatus()) {
            activities.add(activity(
                    CampaignTaskActivityAction.STATUS_CHANGED,
                    details("fromStatus", previous.status().name(), "toStatus", current.getStatus().name())));
        }
        if (!Objects.equals(previous.title(), current.getTitle())) {
            activities.add(activity(
                    CampaignTaskActivityAction.TITLE_UPDATED,
                    details("fromTitle", previous.title(), "toTitle", current.getTitle())));
        }
        if (!Objects.equals(previous.description(), current.getDescription())) {
            activities.add(activity(CampaignTaskActivityAction.DESCRIPTION_UPDATED, Map.of()));
        }
        if (!Objects.equals(previous.dueDate(), current.getDueDate())) {
            activities.add(activity(
                    CampaignTaskActivityAction.DUE_DATE_UPDATED,
                    details(
                            "fromDueDate", toActivityValue(previous.dueDate()),
                            "toDueDate", toActivityValue(current.getDueDate()))));
        }

        Map<Long, String> currentAssignees = assignmentNames(current);
        previous.assignees().forEach((userId, name) -> {
            if (!currentAssignees.containsKey(userId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.ASSIGNEE_REMOVED,
                        details("userId", userId, "name", name)));
            }
        });
        currentAssignees.forEach((userId, name) -> {
            if (!previous.assignees().containsKey(userId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.ASSIGNEE_ADDED,
                        details("userId", userId, "name", name)));
            }
        });

        Map<Long, String> currentLabels = labelNames(current);
        previous.labels().forEach((labelId, name) -> {
            if (!currentLabels.containsKey(labelId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.LABEL_REMOVED,
                        details("labelId", labelId, "name", name)));
            }
        });
        currentLabels.forEach((labelId, name) -> {
            if (!previous.labels().containsKey(labelId)) {
                activities.add(activity(
                        CampaignTaskActivityAction.LABEL_ADDED,
                        details("labelId", labelId, "name", name)));
            }
        });

        return List.copyOf(activities);
    }

    public void record(CampaignTask task, User actor, Collection<Draft> activities) {
        List<CampaignTaskActivity> entities = activities.stream()
                .filter(Objects::nonNull)
                .map(draft -> toEntity(task, actor, draft))
                .toList();
        if (!entities.isEmpty()) {
            campaignTaskActivityRepository.saveAll(entities);
        }
    }

    private CampaignTaskActivity toEntity(CampaignTask task, User actor, Draft draft) {
        return CampaignTaskActivity.builder()
                .task(task)
                .action(draft.action())
                .actor(actor)
                .actorName(displayName(actor))
                .details(draft.details())
                .build();
    }

    public Draft activity(CampaignTaskActivityAction action, Map<String, Object> details) {
        return new Draft(action, details);
    }

    public Map<String, Object> details(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Activity details require key/value pairs");
        }

        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            details.put((String) keyValues[index], keyValues[index + 1]);
        }
        return details;
    }

    public String displayName(User user) {
        if (user == null) {
            return "Unknown user";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail() == null || user.getEmail().isBlank() ? "Unknown user" : user.getEmail();
    }

    private Map<Long, String> assignmentNames(CampaignTask task) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (task.getAssignments() == null) {
            return names;
        }
        task.getAssignments().stream()
                .map(TaskAssignment::getUser)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(User::getId))
                .forEach(user -> names.put(user.getId(), displayName(user)));
        return names;
    }

    private Map<Long, String> labelNames(CampaignTask task) {
        Map<Long, String> names = new LinkedHashMap<>();
        if (task.getLabels() == null) {
            return names;
        }
        task.getLabels().stream()
                .sorted(Comparator.comparing(CampaignTaskLabel::getId))
                .forEach(label -> names.put(label.getId(), label.getName()));
        return names;
    }

    private String toActivityValue(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    public record Draft(CampaignTaskActivityAction action, Map<String, Object> details) {
    }

    public record Snapshot(
            TaskStatus status,
            String title,
            String description,
            LocalDateTime dueDate,
            Map<Long, String> assignees,
            Map<Long, String> labels) {

        public Snapshot {
            assignees = immutableOrderedCopy(assignees);
            labels = immutableOrderedCopy(labels);
        }

        private static Map<Long, String> immutableOrderedCopy(Map<Long, String> values) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
