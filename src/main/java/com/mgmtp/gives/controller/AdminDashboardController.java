package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.dashboard.AdminDashboardOverviewResponse;
import com.mgmtp.gives.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard Management", description = "Endpoints for admin dashboard overview and metrics")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @Operation(summary = "Get admin dashboard overview", description = "Retrieve admin summary metrics for users, campaigns, and donations.")
    public ApiResponse<AdminDashboardOverviewResponse> getAdminDashboardOverview() {
        log.info("Request for admin dashboard overview");
        AdminDashboardOverviewResponse response = dashboardService.getAdminDashboardOverview();
        return ApiResponse.success(response);
    }
}
