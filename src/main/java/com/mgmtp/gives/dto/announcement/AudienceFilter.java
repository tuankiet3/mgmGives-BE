package com.mgmtp.gives.dto.announcement;

public record AudienceFilter(
        Boolean includeMembers,
        Boolean includeFollowers,
        Boolean includeDonors
) {
    public boolean shouldIncludeAll() {
        return !Boolean.TRUE.equals(includeMembers)
                && !Boolean.TRUE.equals(includeFollowers)
                && !Boolean.TRUE.equals(includeDonors);
    }
}
