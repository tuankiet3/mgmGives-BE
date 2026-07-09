package com.mgmtp.gives.dto.donation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DonationResponse {
    private Long id;
    private Long campaignId;
    private String campaignName;
    private String donorName;
    private DonationType type;
    private String amount;
    private String detail;
    
    @JsonProperty("isAnonymous")
    private boolean isAnonymous;
    
    private DonationStatus status;
    private String transactionId;
    private String rejectReason;
    
    private String message;
    
    @JsonProperty("isMessageHidden")
    private boolean isMessageHidden;
    
    private String goodsCondition;
    private String goodsCategory;
    private String deliveryMethod;
    
    private LocalDateTime createdAt;
}
