package com.mgmtp.gives.dto.campaign_spending;

import java.util.List;

public record CampaignSpendingListResponse(
        List<CampaignSpendingResponse> items,
        long totalRaised,
        long totalSpent,
        long remainingFunds
) {
}
