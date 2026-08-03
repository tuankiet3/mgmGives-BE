package com.mgmtp.gives.dto.campaign_member;

public record UnjoinCampaignResponse(UnjoinCampaignResponse.Status status) {
    public enum Status {
        LEFT,
        PENDING_APPROVAL
    }
}
