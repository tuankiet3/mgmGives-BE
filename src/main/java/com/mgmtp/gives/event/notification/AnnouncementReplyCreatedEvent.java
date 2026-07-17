package com.mgmtp.gives.event.notification;

/**
 * Immutable reply details needed to create notifications after the reply transaction commits.
 */
public record AnnouncementReplyCreatedEvent(
        Long campaignId,
        Long announcementId,
        String announcementTitle,
        Long replyId,
        Long replyAuthorId,
        String replyAuthorName,
        Long announcementPublisherId,
        Long referencedReplyAuthorId
) {
}
