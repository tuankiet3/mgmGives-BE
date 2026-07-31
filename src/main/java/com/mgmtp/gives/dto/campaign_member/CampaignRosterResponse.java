package com.mgmtp.gives.dto.campaign_member;

import com.mgmtp.gives.enums.MemberListVisibility;

import java.util.List;

/**
 * Volunteer roster of a campaign, shaped per viewer:
 * admins and members always get the full list; any other signed-in user gets the list only
 * when the campaign's visibility is PUBLIC (minus self-hidden members); a fully anonymous
 * (not signed in) visitor never gets the list, regardless of visibility - just the count.
 *
 * @param visibility     campaign-level roster visibility setting
 * @param totalVolunteers aggregate count, always populated regardless of viewer
 * @param membersVisible whether {@code members} carries the roster for this viewer
 * @param members        roster rows; empty when membersVisible is false
 * @param viewerIsAdmin  viewer manages this campaign (owner or CAMPAIGN_ADMIN member)
 * @param viewerIsMember viewer has a membership row in this campaign
 * @param viewerHidden   the viewer's own hide-from-public-list flag; null when not a member
 */
public record CampaignRosterResponse(
        MemberListVisibility visibility,
        long totalVolunteers,
        boolean membersVisible,
        List<CampaignRosterMemberResponse> members,
        boolean viewerIsAdmin,
        boolean viewerIsMember,
        Boolean viewerHidden
) {
}
