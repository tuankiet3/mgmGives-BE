package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.campaign.CampaignResultDraftContext;
import com.mgmtp.gives.dto.campaign.CampaignResultGenerateResponse;
import com.mgmtp.gives.dto.campaign.DonorThankYouContext;
import com.mgmtp.gives.entity.Campaign;

import java.util.List;
import java.util.Map;

public interface GeminiService {
    /**
     * @return true when the Gemini API key is configured and AI generation can be attempted
     */
    boolean isConfigured();

    CampaignResultGenerateResponse generateCampaignResultDraft(Campaign campaign, CampaignResultDraftContext context);

    /**
     * Generates a personalized thank-you paragraph for each donor.
     * Never throws: donors missing from the returned map should fall back to the static template.
     *
     * @return map of userId to generated message (may be empty or partial on AI failure)
     */
    Map<Long, String> generateDonorThankYouMessages(Campaign campaign, List<DonorThankYouContext> donors);
}
