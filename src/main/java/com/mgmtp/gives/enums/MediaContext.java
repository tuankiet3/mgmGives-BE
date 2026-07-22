package com.mgmtp.gives.enums;

/**
 * Scopes a {@code CampaignMedia} row for gallery visibility. Announcement and meeting
 * attachments are scoped by their own foreign key (announcement_id / meeting_id) and still show
 * up in the general campaign gallery like any other campaign media - only FINAL_REPORT media is
 * excluded from it, since the final report has its own curated list.
 */
public enum MediaContext {
    CAMPAIGN,
    FINAL_REPORT
}
