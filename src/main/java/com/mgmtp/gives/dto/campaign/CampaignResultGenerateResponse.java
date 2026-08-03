package com.mgmtp.gives.dto.campaign;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CampaignResultGenerateResponse(
        @JsonProperty("resultSummary") String resultSummary,
        @JsonProperty("itemsSummary") String itemsSummary,
        @JsonProperty("acknowledgements") String acknowledgements,
        @JsonProperty("taskSummary") String taskSummary
) {}
