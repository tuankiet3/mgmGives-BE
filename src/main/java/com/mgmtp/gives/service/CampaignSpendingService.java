package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingListResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingResponse;
import com.mgmtp.gives.dto.campaign_spending.CreateCampaignSpendingRequest;
import com.mgmtp.gives.dto.campaign_spending.UpdateCampaignSpendingRequest;
import com.mgmtp.gives.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface CampaignSpendingService {

    CampaignSpendingResponse createSpending(Long campaignId, CreateCampaignSpendingRequest request, User currentUser);

    CampaignSpendingResponse updateSpending(Long spendingId, UpdateCampaignSpendingRequest request, User currentUser);

    void deleteSpending(Long spendingId, User currentUser);

    CampaignSpendingListResponse getSpendingsByCampaign(Long campaignId);

    /**
     * Same as {@link #getSpendingsByCampaign(Long)} but skips the donations-raised query,
     * using {@code totalRaised} as supplied by a caller that already computed it (e.g. the
     * final report, which needs the same figure for its own stats).
     */
    CampaignSpendingListResponse getSpendingsByCampaign(Long campaignId, long totalRaised);

    CampaignSpendingResponse addPhoto(Long spendingId, MultipartFile file, User currentUser);

    CampaignSpendingResponse removePhoto(Long spendingId, Long mediaId, User currentUser);
}
