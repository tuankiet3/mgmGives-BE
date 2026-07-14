package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.CampaignResultRequest;
import com.mgmtp.gives.dto.campaign.CampaignResultResponse;
import com.mgmtp.gives.entity.User;

public interface CampaignResultService {
    CampaignResultResponse postResult(Long campaignId, CampaignResultRequest request, User currentUser);
    CampaignResultResponse updateResult(Long campaignId, CampaignResultRequest request, User currentUser);
    CampaignResultResponse getResult(Long campaignId);
    CampaignResultGenerateResponse generateResultDraft(Long campaignId, User currentUser);
    byte[] generateResultPdf(Long campaignId);
}
