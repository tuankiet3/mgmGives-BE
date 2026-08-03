package com.mgmtp.gives.service.support;

import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.CampaignTaskActivity;
import com.mgmtp.gives.entity.CampaignTaskLabel;
import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignTaskActivityAction;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.repository.CampaignTaskActivityRepository;
import com.mgmtp.gives.service.support.CampaignTaskActivityTracker.Draft;
import com.mgmtp.gives.service.support.CampaignTaskActivityTracker.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CampaignTaskActivityTrackerTest {

    @Mock
    CampaignTaskActivityRepository campaignTaskActivityRepository;

    @InjectMocks
    CampaignTaskActivityTracker tracker;

    @Test
    void snapshotCapturesStableOrderedAssigneeAndLabelNames() {
        User emailFallback = user(2L, " ", "fallback@example.test");
        User namedUser = user(1L, "Alice Admin", "alice@example.test");
        CampaignTask task = task(
                TaskStatus.TODO,
                "Prepare CV",
                null,
                null,
                Set.of(assignment(emailFallback), assignment(namedUser)),
                Set.of(label(20L, "Backend"), label(10L, "Urgent")));

        Snapshot snapshot = tracker.snapshot(task);
        namedUser.setFullName("Changed after snapshot");
        task.getLabels().clear();

        assertThat(snapshot.assignees()).containsExactly(
                Map.entry(1L, "Alice Admin"),
                Map.entry(2L, "fallback@example.test"));
        assertThat(snapshot.labels()).containsExactly(
                Map.entry(10L, "Urgent"),
                Map.entry(20L, "Backend"));
        assertThatThrownBy(() -> snapshot.assignees().put(3L, "Blocked"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void collectChangesProducesOneAuditDraftForEveryChangedFieldAndRelation() {
        LocalDateTime previousDueDate = LocalDateTime.of(2026, 8, 4, 9, 30);
        Map<Long, String> previousAssignees = new LinkedHashMap<>();
        previousAssignees.put(1L, "Alice");
        previousAssignees.put(2L, "Bob");
        Map<Long, String> previousLabels = new LinkedHashMap<>();
        previousLabels.put(10L, "Urgent");
        previousLabels.put(20L, "Frontend");
        Snapshot previous = new Snapshot(
                TaskStatus.TODO,
                "Before",
                "Old description",
                previousDueDate,
                previousAssignees,
                previousLabels);
        CampaignTask current = task(
                TaskStatus.DONE,
                "After",
                "New description",
                null,
                Set.of(
                        assignment(user(2L, "Bob", "bob@example.test")),
                        assignment(user(3L, "Carla", "carla@example.test"))),
                Set.of(label(20L, "Frontend"), label(30L, "Backend")));

        List<Draft> changes = tracker.collectChanges(previous, current);

        assertThat(changes).extracting(Draft::action).containsExactly(
                CampaignTaskActivityAction.STATUS_CHANGED,
                CampaignTaskActivityAction.TITLE_UPDATED,
                CampaignTaskActivityAction.DESCRIPTION_UPDATED,
                CampaignTaskActivityAction.DUE_DATE_UPDATED,
                CampaignTaskActivityAction.ASSIGNEE_REMOVED,
                CampaignTaskActivityAction.ASSIGNEE_ADDED,
                CampaignTaskActivityAction.LABEL_REMOVED,
                CampaignTaskActivityAction.LABEL_ADDED);
        assertThat(changes.get(3).details())
                .containsEntry("fromDueDate", previousDueDate.toString())
                .containsEntry("toDueDate", null);
        assertThat(changes.get(4).details()).containsExactly(
                Map.entry("userId", 1L),
                Map.entry("name", "Alice"));
        assertThat(changes.get(7).details()).containsExactly(
                Map.entry("labelId", 30L),
                Map.entry("name", "Backend"));
    }

    @Test
    void recordPersistsActorSnapshotAndSkipsNullDrafts() {
        CampaignTask task = task(TaskStatus.TODO, "Audit", null, null, Set.of(), Set.of());
        User actor = user(7L, "", "actor@example.test");
        Draft draft = tracker.activity(
                CampaignTaskActivityAction.TASK_CREATED,
                tracker.details("status", TaskStatus.TODO.name()));

        tracker.record(task, actor, Arrays.asList(null, draft));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CampaignTaskActivity>> captor = ArgumentCaptor.forClass(List.class);
        verify(campaignTaskActivityRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(activity -> {
            assertThat(activity.getTask()).isSameAs(task);
            assertThat(activity.getActor()).isSameAs(actor);
            assertThat(activity.getActorName()).isEqualTo("actor@example.test");
            assertThat(activity.getAction()).isEqualTo(CampaignTaskActivityAction.TASK_CREATED);
            assertThat(activity.getDetails()).containsEntry("status", "TODO");
        });
    }

    @Test
    void detailsPreservesOrderAndNullValuesAndRejectsIncompletePairs() {
        Map<String, Object> details = tracker.details("first", 1L, "second", null);

        assertThat(details.keySet()).containsExactly("first", "second");
        assertThat(details).containsEntry("first", 1L).containsKey("second");
        assertThat(details.get("second")).isNull();
        assertThatThrownBy(() -> tracker.details("missingValue"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key/value pairs");
    }

    private static CampaignTask task(
            TaskStatus status,
            String title,
            String description,
            LocalDateTime dueDate,
            Set<TaskAssignment> assignments,
            Set<CampaignTaskLabel> labels) {
        return CampaignTask.builder()
                .status(status)
                .title(title)
                .description(description)
                .dueDate(dueDate)
                .assignments(new java.util.HashSet<>(assignments))
                .labels(new java.util.HashSet<>(labels))
                .build();
    }

    private static TaskAssignment assignment(User user) {
        return TaskAssignment.builder().user(user).build();
    }

    private static CampaignTaskLabel label(Long id, String name) {
        return CampaignTaskLabel.builder().id(id).name(name).build();
    }

    private static User user(Long id, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEmail(email);
        return user;
    }
}
