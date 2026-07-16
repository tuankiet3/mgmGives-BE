package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.dto.campaign_task.CreateCampaignTaskRequest;
import com.mgmtp.gives.dto.campaign_task.UpdateCampaignTaskRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.repository.CampaignLabelRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CampaignTaskActivityRepository;
import com.mgmtp.gives.repository.CampaignTaskRepository;
import com.mgmtp.gives.repository.TaskAssignmentRepository;
import com.mgmtp.gives.repository.TaskAttachmentRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.impl.CampaignTaskServiceImpl;
import com.mgmtp.gives.util.CampaignAccessHelper;
import jakarta.validation.Validation;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignTaskServiceImplTest {

        @Mock
        CampaignTaskRepository campaignTaskRepository;
        @Mock
        CampaignRepository campaignRepository;
        @Mock
        CampaignLabelRepository campaignLabelRepository;
        @Mock
        CampaignMemberRepository campaignMemberRepository;
        @Mock
        UserRepository userRepository;
        @Mock
        TaskAssignmentRepository taskAssignmentRepository;
        @Mock
        TaskAttachmentRepository taskAttachmentRepository;
        @Mock
        CampaignAccessHelper campaignAccessHelper;
        @Mock
        MediaService mediaService;
        @Mock
        ApplicationEventPublisher eventPublisher;
        @Mock
        CampaignTaskActivityRepository campaignTaskActivityRepository;

        @InjectMocks
        CampaignTaskServiceImpl service;

        @Test
        void createTaskUsesRequestedKanbanStatus() {
                User admin = user(7L);
                Campaign campaign = new Campaign();
                campaign.setId(3L);
                when(campaignAccessHelper.findCampaignOrThrow(3L)).thenReturn(campaign);
                when(campaignRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(campaign));
                when(campaignTaskRepository.save(any(CampaignTask.class))).thenAnswer(invocation -> {
                        CampaignTask task = invocation.getArgument(0);
                        task.setId(11L);
                        return task;
                });

                CampaignTaskResponse result = service.createTask(
                                3L,
                                new CreateCampaignTaskRequest("Launch", "Prepare launch materials", null, List.of(),
                                                List.of(), TaskStatus.DONE),
                                admin);

                assertEquals(TaskStatus.DONE, result.status());
                assertEquals(1L, result.position());
                assertEquals(11L, result.id());
        }

        @Test
        void movingTaskToAnotherStatusAppendsItToTheDestinationColumn() {
                User admin = user(7L);
                Campaign campaign = new Campaign();
                campaign.setId(3L);
                CampaignTask task = CampaignTask.builder()
                                .campaign(campaign)
                                .title("Move me")
                                .status(TaskStatus.TODO)
                                .position(2L)
                                .createdBy(admin)
                                .build();
                task.setId(11L);

                when(campaignTaskRepository.findById(11L)).thenReturn(Optional.of(task));
                when(campaignAccessHelper.isCampaignAdmin(3L, admin)).thenReturn(true);
                when(campaignRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(campaign));
                when(campaignTaskRepository.findMaxActivePositionByCampaignIdAndStatus(3L, TaskStatus.DONE))
                                .thenReturn(6L);
                when(campaignTaskRepository.save(task)).thenReturn(task);

                CampaignTaskResponse result = service.updateTask(
                                11L,
                                new UpdateCampaignTaskRequest(null, null, null, null, TaskStatus.DONE, null, null,
                                                null),
                                admin);

                assertEquals(TaskStatus.DONE, result.status());
                assertEquals(7L, result.position());
        }

        @Test
        void createTaskRequestAllowsTitleOnly() {
                var validator = Validation.buildDefaultValidatorFactory().getValidator();
                var request = new CreateCampaignTaskRequest(
                                "Launch",
                                null,
                                null,
                                null,
                                null,
                                TaskStatus.TODO);

                var violations = validator.validate(request);

                assertTrue(violations.isEmpty());
        }

        @Test
        void unarchiveTaskAppendsItToTheActiveColumn() {
                User admin = user(7L);
                Campaign campaign = new Campaign();
                campaign.setId(3L);
                CampaignTask task = CampaignTask.builder()
                                .campaign(campaign)
                                .title("Archived task")
                                .status(TaskStatus.TODO)
                                .position(2L)
                                .isArchived(true)
                                .createdBy(admin)
                                .build();
                task.setId(11L);

                when(campaignTaskRepository.findById(11L)).thenReturn(Optional.of(task));
                when(campaignRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(campaign));
                when(campaignTaskRepository.findMaxActivePositionByCampaignIdAndStatus(3L, TaskStatus.TODO))
                                .thenReturn(2L);
                when(campaignTaskRepository.save(task)).thenReturn(task);

                CampaignTaskResponse result = service.unarchiveTask(11L, admin);

                assertEquals(3L, result.position());
                assertEquals(false, result.isArchived());
        }

        @Test
        void restoreDeletedTaskClearsDeletedTimestampAndPreservesArchiveState() {
                User admin = user(7L);
                Campaign campaign = new Campaign();
                campaign.setId(3L);
                CampaignTask task = CampaignTask.builder()
                                .campaign(campaign)
                                .title("Archived before deletion")
                                .status(TaskStatus.TODO)
                                .createdBy(admin)
                                .isArchived(true)
                                .deletedAt(LocalDateTime.now())
                                .build();
                task.setId(11L);
                when(campaignTaskRepository.findById(11L)).thenReturn(Optional.of(task));
                when(campaignTaskRepository.save(task)).thenReturn(task);

                CampaignTaskResponse result = service.restoreDeletedTask(11L, admin);

                assertNull(result.deletedAt());
                assertEquals(true, result.isArchived());
        }

        @Test
        @SuppressWarnings("unchecked")
        void listTasksValidatesCampaignReadAccess() {
                User member = user(8L);
                when(campaignTaskRepository.findAll(any(Specification.class), any(PageRequest.class)))
                                .thenReturn(new PageImpl<>(List.of()));

                service.getTasksByCampaign(3L, null, null, false, false, PageRequest.of(0, 10), member);

                verify(campaignAccessHelper).validateCampaignMemberOrAdmin(
                                eq(3L), eq(member), any());
        }

        @Test
        void createTaskPublishesTaskCreatedEmailEventWhenAssigneesExist() {
                User admin = user(7L);
                Campaign campaign = new Campaign();
                campaign.setId(3L);
                campaign.setTitle("mgmGives Campaign");

                User assignee = user(8L);
                assignee.setStatus(com.mgmtp.gives.enums.UserStatus.ACTIVE);

                when(campaignAccessHelper.findCampaignOrThrow(3L)).thenReturn(campaign);
                when(campaignRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(campaign));
                when(userRepository.findById(8L)).thenReturn(Optional.of(assignee));
                when(campaignAccessHelper.isCampaignMember(3L, 8L)).thenReturn(true);

                when(campaignTaskRepository.save(any(CampaignTask.class))).thenAnswer(invocation -> {
                        CampaignTask task = invocation.getArgument(0);
                        task.setId(11L);
                        return task;
                });

                CreateCampaignTaskRequest request = new CreateCampaignTaskRequest(
                                "Launch",
                                "Prepare launch materials",
                                null,
                                List.of(8L),
                                List.of(),
                                TaskStatus.TODO);

                service.createTask(3L, request, admin);

                verify(eventPublisher)
                                .publishEvent(any(com.mgmtp.gives.event.notification.TaskCreatedEmailEvent.class));
                verify(eventPublisher).publishEvent(any(com.mgmtp.gives.event.notification.TaskAssignedEvent.class));
        }

        @Test
        void addAssigneePublishesTaskCreatedEmailEvent() {
                User admin = user(7L);
                Campaign campaign = new Campaign();
                campaign.setId(3L);
                campaign.setTitle("mgmGives Campaign");

                CampaignTask task = CampaignTask.builder()
                                .campaign(campaign)
                                .title("Move me")
                                .status(TaskStatus.TODO)
                                .createdBy(admin)
                                .build();
                task.setId(11L);
                task.setAssignments(new java.util.HashSet<>());

                User assignee = user(8L);
                assignee.setStatus(com.mgmtp.gives.enums.UserStatus.ACTIVE);

                when(campaignTaskRepository.findById(11L)).thenReturn(Optional.of(task));
                when(campaignAccessHelper.isCampaignMember(3L, 8L)).thenReturn(true);
                when(userRepository.findById(8L)).thenReturn(Optional.of(assignee));
                when(campaignTaskRepository.save(any(CampaignTask.class))).thenReturn(task);

                service.addAssignee(11L, 8L, admin);

                verify(eventPublisher).publishEvent(any(com.mgmtp.gives.event.notification.TaskAssignedEvent.class));
                verify(eventPublisher)
                                .publishEvent(any(com.mgmtp.gives.event.notification.TaskCreatedEmailEvent.class));
        }

        private static User user(Long id) {
                User user = new User();
                user.setId(id);
                user.setFullName("User " + id);
                user.setEmail("user" + id + "@example.com");
                return user;
        }
}
