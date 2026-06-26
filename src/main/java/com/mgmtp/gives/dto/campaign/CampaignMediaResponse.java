package com.mgmtp.gives.dto.campaign;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignMediaResponse {
    private Long id;
    private String url;
    private String mediaType;
}
