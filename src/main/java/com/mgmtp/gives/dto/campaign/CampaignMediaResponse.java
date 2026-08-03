package com.mgmtp.gives.dto.campaign;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignMediaResponse {
    private Long id;
    private String url;
    private String mediaType;

    @JsonProperty("isCover")
    private boolean isCover;
    private String caption;
    private Integer displayOrder;
    private String context;
}
