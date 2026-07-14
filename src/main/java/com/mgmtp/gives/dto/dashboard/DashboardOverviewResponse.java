package com.mgmtp.gives.dto.dashboard;

import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.dto.donation.DonationResponse;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DashboardOverviewResponse {
    private Long totalDonatedAmount;
    private Long followedCampaignsCount;
    private Long completedCampaignsCount;
    private List<CampaignResponse> recommendedCampaigns;
    private List<DonationResponse> recentDonations;
    private List<ActivityDTO> recentActivities;
}
