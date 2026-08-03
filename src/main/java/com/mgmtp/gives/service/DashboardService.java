package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.dashboard.DashboardOverviewResponse;
import com.mgmtp.gives.dto.dashboard.AdminDashboardOverviewResponse;
import com.mgmtp.gives.entity.User;

public interface DashboardService {
    DashboardOverviewResponse getDashboardOverview(User currentUser);
    AdminDashboardOverviewResponse getAdminDashboardOverview();
}
