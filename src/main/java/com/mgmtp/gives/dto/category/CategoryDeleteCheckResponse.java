package com.mgmtp.gives.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryDeleteCheckResponse(
    @Schema(description = "The number of campaigns assigned to this category", example = "5")
    long assignedCampaignsCount,

    @Schema(description = "The number of campaigns where this is the only category", example = "2")
    long onlyCategoryCampaignsCount
) {}
