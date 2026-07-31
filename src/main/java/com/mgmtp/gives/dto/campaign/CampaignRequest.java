package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationMethod;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.Set;

public record CampaignRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 100, message = "Title must not exceed 100 characters")
        String title,
        String description,
        Set<Long> categories,
        Boolean acceptsMoney,
        Boolean acceptsGoods,
        Long target,
        LocalDateTime startDate,
        LocalDateTime endDate,
        CampaignPriority priority,
        CampaignStatus status,
        DonationMethod donationMethod,
        String bankName,
        String bankCode,
        String bankBin,
        String bankAccountNumber,
        String bankAccountHolderName
) {
    public CampaignRequest(
            String title,
            String description,
            Set<Long> categories,
            Boolean acceptsMoney,
            Boolean acceptsGoods,
            Long target,
            LocalDateTime startDate,
            LocalDateTime endDate,
            CampaignPriority priority,
            CampaignStatus status
    ) {
        this(title, description, categories, acceptsMoney, acceptsGoods, target, startDate, endDate, priority, status, DonationMethod.MANUAL_QR, null, null, null, null, null);
    }
}
