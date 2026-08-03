package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mgmtp.gives.entity.CampaignMedia;
import java.util.List;

public interface AdminCampaignService {
    Page<Campaign> getPendingCampaigns(Pageable pageable);

    Page<Campaign> getCampaigns(CampaignStatus status, List<Long> categoryIds, String keyword, Pageable pageable);

    Campaign getCampaignById(Long id);

    Campaign approveCampaign(Long id, User adminUser);

    Campaign rejectCampaign(Long id, String reason, User adminUser);

    List<CampaignMedia> getActiveMediasByCampaignId(Long campaignId);

}
