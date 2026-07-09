package com.mgmtp.gives.dto.campaign_follower;

import lombok.Data;
import java.util.List;

@Data
public class CampaignFollowerFilterCriteria {
    private String keyword;
    private String status;
    private String priority;
    private List<Long> categoryIds;
}
