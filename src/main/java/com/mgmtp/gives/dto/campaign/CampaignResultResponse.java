package com.mgmtp.gives.dto.campaign;

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
    private String publishedByName;
    private LocalDateTime publishedAt;

    // Auto-computed stats
    private Long totalRaised;
    private long donorCount;
    private long volunteerCount;
    private double goalPercent;

    // Outcome media (context = RESULT)
    private List<CampaignMediaResponse> resultMedias;
}
