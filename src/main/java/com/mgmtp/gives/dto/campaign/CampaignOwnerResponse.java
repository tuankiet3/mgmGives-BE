package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.dto.category.CategoryResponse;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CampaignOwnerResponse {
    private Long id;
    private String title;
    private String description;
    private CampaignStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long target;
    private CampaignPriority priority;
    private Long creatorId;
    private String creatorName;
    private String creatorAvatarUrl;

    // Review outcome visible to the owner
    private String rejectionReason;
    private LocalDateTime approvedAt;

    private List<CategoryResponse> categories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
