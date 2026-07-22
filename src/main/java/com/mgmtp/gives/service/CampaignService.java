package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import com.mgmtp.gives.entity.CampaignMedia;

public interface CampaignService {
    Campaign createCampaign(CampaignRequest request, User currentUser);

    Page<Campaign> getAllCampaigns(
            CampaignStatus status,
            CampaignPriority priority,
            List<Long> categoryIds,
            Long userId,
            String keyword,
            Boolean isFollowing,
            User currentUser,
            Pageable pageable);

    Campaign getCampaignById(Long id, User currentUser);

    Campaign updateCampaign(Long id, CampaignRequest request, User currentUser);

    void deleteCampaign(Long id, User currentUser);

    Campaign endCampaign(Long id, User currentUser);

    void startApprovedCampaignsScheduled();

    void completeEndedCampaignsScheduled();

    List<CampaignMedia> getActiveMediasByCampaignId(Long campaignId);

    CampaignResponse toResponse(Campaign campaign, User currentUser);

    List<CampaignResponse> toResponseList(List<Campaign> campaigns, User currentUser);
    boolean isFollowed(Long campaignId, Long userId);

    boolean isJoined(Long campaignId, Long userId);

    long getVolunteersCount(Long campaignId);

    long getDonorsCount(Long campaignId);
}
