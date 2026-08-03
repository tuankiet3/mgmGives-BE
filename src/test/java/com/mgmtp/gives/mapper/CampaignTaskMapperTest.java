package com.mgmtp.gives.mapper;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.CampaignTaskActivity;
import com.mgmtp.gives.entity.CampaignTaskLabel;
import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.entity.TaskAttachment;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignTaskActivityAction;
import com.mgmtp.gives.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignTaskMapperTest {

    private final CampaignTaskMapper mapper = new CampaignTaskMapper();

    @Test
    void toResponseMapsNestedTaskDetailsWithoutPersistenceDependencies() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 3, 12, 30);
        User creator = user(1L, "Creator", "creator@example.test", "/creator.png");
        User assignee = user(2L, "Assignee", "assignee@example.test", "/assignee.png");
        User uploader = user(3L, "Uploader", "uploader@example.test", "/uploader.png");

        Campaign campaign = new Campaign();
        campaign.setId(10L);
        CampaignTask task = CampaignTask.builder()
                .campaign(campaign)
                .title("Prepare launch")
                .description("Coordinate the release")
                .status(TaskStatus.IN_PROGRESS)
                .position(4L)
                .version(7L)
                .dueDate(timestamp.plusDays(3))
                .createdBy(creator)
                .updatedAt(timestamp.plusHours(1))
                .isArchived(true)
                .deletedAt(timestamp.plusDays(1))
                .build();
        task.setId(20L);
        task.setCreatedAt(timestamp);

        task.setAssignments(Set.of(TaskAssignment.builder().task(task).user(assignee).build()));
        task.setLabels(Set.of(CampaignTaskLabel.builder()
                .id(30L)
                .campaign(campaign)
                .name("Urgent")
                .color("#ef4444")
                .build()));
        task.setAttachments(Set.of(TaskAttachment.builder()
                .id(40L)
                .task(task)
                .originalFilename("brief.pdf")
                .storedFilename("40-brief.pdf")
                .fileType("application/pdf")
                .fileSize(512L)
                .uploadedBy(uploader)
                .uploadedAt(timestamp.plusMinutes(5))
                .build()));

        var response = mapper.toResponse(task);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.campaignId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.createdBy()).extracting("id", "fullName", "email", "avatarUrl")
                .containsExactly(1L, "Creator", "creator@example.test", "/creator.png");
        assertThat(response.assignees()).singleElement()
                .extracting("id", "fullName", "email", "avatarUrl")
                .containsExactly(2L, "Assignee", "assignee@example.test", "/assignee.png");
        assertThat(response.labels()).singleElement()
                .extracting("id", "name", "color")
                .containsExactly(30L, "Urgent", "#ef4444");
        assertThat(response.attachments()).singleElement()
                .extracting("id", "originalFilename", "url", "fileType", "fileSize")
                .containsExactly(40L, "brief.pdf", "40-brief.pdf", "application/pdf", 512L);
        assertThat(response.attachments().getFirst().uploadedBy())
                .extracting("id", "fullName", "email", "avatarUrl")
                .containsExactly(3L, "Uploader", "uploader@example.test", "/uploader.png");
    }

    @Test
    void toResponseSupportsTasksWithoutCampaignCreatorOrUploader() {
        CampaignTask task = CampaignTask.builder()
                .title("Detached task")
                .status(TaskStatus.TODO)
                .version(0L)
                .build();
        task.setAttachments(Set.of(TaskAttachment.builder().id(40L).fileSize(0L).build()));

        var response = mapper.toResponse(task);

        assertThat(response.campaignId()).isNull();
        assertThat(response.createdBy()).isNull();
        assertThat(response.attachments()).singleElement()
                .extracting("uploadedBy")
                .isNull();
    }

    @Test
    void toActivityResponsePreservesSnapshotNameWhenActorWasRemoved() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 3, 12, 30);
        CampaignTaskActivity activity = CampaignTaskActivity.builder()
                .id(50L)
                .action(CampaignTaskActivityAction.ASSIGNEE_REMOVED)
                .actor(null)
                .actorName("Former member")
                .details(Map.of("userId", 2L))
                .createdAt(timestamp)
                .build();

        var response = mapper.toActivityResponse(activity);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.actor().id()).isNull();
        assertThat(response.actor().name()).isEqualTo("Former member");
        assertThat(response.actor().avatarUrl()).isNull();
        assertThat(response.details()).containsEntry("userId", 2L);
        assertThat(response.createdAt()).isEqualTo(timestamp);
    }

    private static User user(Long id, String name, String email, String avatarUrl) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        return user;
    }
}
