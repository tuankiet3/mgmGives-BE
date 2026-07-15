package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign_label.CampaignLabelResponse;
import com.mgmtp.gives.dto.campaign_label.CreateCampaignLabelRequest;
import com.mgmtp.gives.dto.campaign_label.UpdateCampaignLabelRequest;
import com.mgmtp.gives.entity.User;

import java.util.List;

public interface CampaignLabelService {
    CampaignLabelResponse createLabel(Long campaignId, CreateCampaignLabelRequest request, User currentUser);

    CampaignLabelResponse updateLabel(Long labelId, UpdateCampaignLabelRequest request, User currentUser);

    void deleteLabel(Long labelId, User currentUser);

    List<CampaignLabelResponse> getLabelsByCampaign(Long campaignId, User currentUser);
}
