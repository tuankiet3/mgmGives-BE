package com.mgmtp.gives.dto.announcement;

public record ReplyWebSocketEvent(
        String action, // "CREATED" | "EDITED" | "DELETED"
        Long replyId,
        Long announcementId,
        AnnouncementReplyResponse reply // Null for CREATED and DELETED
) {
}
