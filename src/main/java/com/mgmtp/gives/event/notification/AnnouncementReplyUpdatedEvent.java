package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.dto.announcement.AnnouncementReplyResponse;

public record AnnouncementReplyUpdatedEvent(
        Long campaignId,
        Long announcementId,
        Long replyId,
        AnnouncementReplyResponse reply
) {
}
