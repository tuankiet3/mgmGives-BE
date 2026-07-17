package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.announcement.*;
import com.mgmtp.gives.entity.User;

public interface AnnouncementReplyService {
    AnnouncementReplyResponse createReply(Long campaignId, Long announcementId, CreateReplyRequest request, User currentUser);
    AnnouncementReplyResponse updateReply(Long campaignId, Long announcementId, Long replyId, UpdateReplyRequest request, User currentUser);
    void deleteReply(Long campaignId, Long announcementId, Long replyId, User currentUser);
    ReplyPageResponse<AnnouncementReplyResponse> getReplies(Long campaignId, Long announcementId, Long cursor, int limit, String sort, User currentUser);
    ReplyContextResponse getReplyContext(Long campaignId, Long announcementId, Long replyId, Long cursor, String direction, int limit, String sort, User currentUser);
}
