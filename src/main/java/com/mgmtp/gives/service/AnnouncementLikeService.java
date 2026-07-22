package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.User;

public interface AnnouncementLikeService {
    void likeAnnouncement(Long campaignId, Long announcementId, User currentUser);
    void unlikeAnnouncement(Long campaignId, Long announcementId, User currentUser);
}
