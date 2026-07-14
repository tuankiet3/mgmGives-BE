package com.mgmtp.gives.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardOverviewResponse {
    private Long totalEmployees;
    private Long campaignAdmins;
    private Long totalCampaigns;
    private Long pendingCampaigns;
    private Long activeCampaigns;
    private Long totalDonation;
}
