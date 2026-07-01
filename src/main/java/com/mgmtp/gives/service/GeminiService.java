package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.entity.Campaign;

public interface GeminiService {
    CampaignResultGenerateResponse generateCampaignResultDraft(
            Campaign campaign, long totalRaised, long donorCount, long volunteerCount, double goalPercent);
}
