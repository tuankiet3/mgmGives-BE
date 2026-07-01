package com.mgmtp.gives.dto.campaign;

import jakarta.validation.constraints.NotBlank;

public record CampaignResultRequest(
        @NotBlank(message = "Result summary is required") String resultSummary,
        Long finalAmountRaised,
        String itemsSummary,
        String acknowledgements
) {}
