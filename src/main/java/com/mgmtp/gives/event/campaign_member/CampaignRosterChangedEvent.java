package com.mgmtp.gives.event.campaign_member;

/**
 * Fired after any change that affects a campaign's volunteer roster or its visibility
 * (join, leave, visibility setting, member self-hide). Carries no member data on purpose:
 * subscribers refetch through the permission-aware roster endpoint, so a broadcast topic
 * cannot leak anything the viewer is not allowed to see.
 */
public record CampaignRosterChangedEvent(Long campaignId) {
}
