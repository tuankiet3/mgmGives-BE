package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.dashboard.DashboardOverviewResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Management", description = "Endpoints for employee dashboard overview and metrics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Get employee dashboard overview", description = "Retrieve summary metrics, recommended campaigns, recent donations, and activities for the logged-in employee.")
    public ApiResponse<DashboardOverviewResponse> getDashboardOverview(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Request for dashboard overview from user: {}", userDetails.getUser().getEmail());
        DashboardOverviewResponse response = dashboardService.getDashboardOverview(userDetails.getUser());
        return ApiResponse.success(response);
    }
}
