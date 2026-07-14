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
public class AdminCampaignResponse {
    private Long id;
    private String title;
    private String description;
    private CampaignStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long target;
    private Long currentRaised;
    private CampaignPriority priority;

    // Submitter info
    private Long creatorId;
    private String creatorName;
    private String creatorAvatarUrl;
    private String creatorEmail;

    // Review metadata
    private String rejectionReason;
    private LocalDateTime approvedAt;
    private Long approvedById;
    private String approvedByName;

    private List<CategoryResponse> categories;
    private List<CampaignMediaResponse> medias;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
