package com.mgmtp.gives.dto.announcement;

import java.util.List;

/**
 * A contiguous reply window centered on an anchor reply, with independent
 * cursors for extending the window toward newer and older replies.
 */
public record ReplyContextResponse(
        List<AnnouncementReplyResponse> content,
        Long anchorReplyId,
        Long newerCursor,
        Long olderCursor,
        boolean hasNewer,
        boolean hasOlder
) {
}
