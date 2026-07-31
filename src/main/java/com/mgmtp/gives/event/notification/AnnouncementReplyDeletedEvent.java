package com.mgmtp.gives.event.notification;

public record AnnouncementReplyDeletedEvent(
        Long campaignId,
        Long announcementId,
        Long replyId
) {
}
