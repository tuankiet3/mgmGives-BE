package com.mgmtp.gives.dto.campaign_task;

import com.mgmtp.gives.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record MoveCampaignTaskRequest(
        @NotNull TaskStatus status,
        @NotNull @PositiveOrZero Long expectedVersion,
        @Positive Long position
) {
}
