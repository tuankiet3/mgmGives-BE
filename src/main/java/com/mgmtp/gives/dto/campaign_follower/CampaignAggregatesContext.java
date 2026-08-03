package com.mgmtp.gives.dto.campaign_follower;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignAggregatesContext {
    private Map<Long, Long> amountsMap;
    private Map<Long, Long> donorsMap;
    private Map<Long, Long> volunteersMap;
}
