package com.mgmtp.gives.enums;

/**
 * Campaign-level setting controlling who can see the volunteer roster.
 * MEMBERS_ONLY (default): only the campaign admin and joined members see the member list;
 * everyone else sees just the aggregate volunteer count.
 * PUBLIC: anyone can see the list, minus members who individually opted out
 * (CampaignMember.hiddenFromPublicList).
 */
public enum MemberListVisibility {
    MEMBERS_ONLY,
    PUBLIC
}
