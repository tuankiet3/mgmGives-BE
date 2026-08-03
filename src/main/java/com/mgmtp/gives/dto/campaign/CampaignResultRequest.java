package com.mgmtp.gives.dto.campaign;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CampaignResultRequest(
        @NotBlank(message = "Result summary is required") String resultSummary,
        Long finalAmountRaised,
        String itemsSummary,
        String acknowledgements,
        String taskSummary,
        List<Long> mediaIds
) {}
