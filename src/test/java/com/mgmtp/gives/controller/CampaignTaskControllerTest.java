package com.mgmtp.gives.controller;

import com.mgmtp.gives.dto.campaign_task.CampaignTaskResponse;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignTaskControllerTest {

    private CampaignTaskService service;
    private CampaignTaskController controller;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        service = mock(CampaignTaskService.class);
        controller = new CampaignTaskController(service);
        User user = new User();
        user.setId(9L);
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void getTasksForwardsDeletedViewAndAuthenticatedUser() {
        PageRequest pageable = PageRequest.of(0, 100);
        when(service.getTasksByCampaign(4L, TaskStatus.TODO, null, false, true, pageable,
                userDetails.getUser())).thenReturn(new PageImpl<>(List.of()));

        controller.getTasks(4L, TaskStatus.TODO, null, false, true, pageable, userDetails);

        verify(service).getTasksByCampaign(4L, TaskStatus.TODO, null, false, true, pageable,
                userDetails.getUser());
    }

    @Test
    void restoreAndUnarchiveUseDifferentLifecycleOperations() {
        CampaignTaskResponse response = mock(CampaignTaskResponse.class);
        when(service.restoreDeletedTask(5L, userDetails.getUser())).thenReturn(response);
        when(service.unarchiveTask(5L, userDetails.getUser())).thenReturn(response);

        controller.restoreDeletedTask(5L, userDetails);
        controller.unarchiveTask(5L, userDetails);

        verify(service).restoreDeletedTask(5L, userDetails.getUser());
        verify(service).unarchiveTask(5L, userDetails.getUser());
    }
}
