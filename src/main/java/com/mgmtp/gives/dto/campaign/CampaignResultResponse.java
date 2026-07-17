package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CampaignResultResponse {
    private Long campaignId;
    private String resultSummary;
    private Long finalAmountRaised;
    private String itemsSummary;
    private String acknowledgements;
    private String taskSummary;
    private String publishedByName;
    private LocalDateTime publishedAt;
    private List<CampaignMediaResponse> media;

    // Auto-computed stats
    private Long totalRaised;
    private long donorCount;
    private long volunteerCount;
    private double goalPercent;
    private long taskCount;
    private long completedTaskCount;

    // Spending ledger, computed live (not part of the AI draft/publish workflow)
    private List<CampaignSpendingResponse> spendingItems;
    private long totalSpent;
    private long remainingFunds;
}
