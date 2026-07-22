package com.mgmtp.gives.dto.campaign_member;

import lombok.Data;
import java.util.List;

@Data
public class CampaignMemberFilterCriteria {
    private String keyword;
    private String status;
    private String priority;
    private List<Long> categoryIds;
}
