package com.mgmtp.gives.dto.campaign;

import com.mgmtp.gives.dto.category.CategoryResponse;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationMethod;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CampaignResponse {
    private Long id;
    private String title;
    private String description;
    private CampaignStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long target;
    private Long currentRaised;
    private CampaignPriority priority;
    private Boolean acceptsMoney;
    private Boolean acceptsGoods;
    private String rejectionReason;
    private Long creatorId;
    private String creatorName;
    private String creatorAvatarUrl;
    private List<CategoryResponse> categories;
    private List<CampaignMediaResponse> medias;
    private List<CampaignMediaResponse> media;
    private String coverImageUrl;
    private Boolean isEditable;
    private Boolean isFollowed;
    private Boolean isJoined;
    private Boolean hasPendingUnjoinRequest;
    private Long volunteersCount;
    private Long donorsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private boolean isCampaignAdmin;
    private boolean resultPosted;
    private LocalDateTime resultPublishedAt;
    private String resultPublishedByName;

    private DonationMethod donationMethod;
    private String bankName;
    private String bankCode;
    private String bankBin;
    private String bankAccountNumber;
    private String bankAccountHolderName;
    private Boolean creatorHasPayOS;
}
