package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.AnnouncementLikeRepository;
import com.mgmtp.gives.repository.AnnouncementRepository;
import com.mgmtp.gives.security.AnnouncementAccessAuthorizer;
import com.mgmtp.gives.service.AnnouncementLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementLikeServiceImpl implements AnnouncementLikeService {

    private final AnnouncementLikeRepository likeRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementAccessAuthorizer announcementAccessAuthorizer;

    @Override
    @Transactional
    public void likeAnnouncement(Long campaignId, Long announcementId, User currentUser) {
        log.info("Processing like for announcement: campaignId={}, announcementId={}, userId={}", campaignId, announcementId, currentUser.getId());

        Announcement announcement = announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);

        int affected = likeRepository.insertIgnore(announcement.getId(), currentUser.getId());
        if (affected == 1) {
            // The bulk counter update is atomic with the like insert in this transaction.
            announcementRepository.incrementLikesCount(announcement.getId());
            log.info("Announcement liked successfully: announcementId={}, userId={}", announcementId, currentUser.getId());
        } else {
            log.debug("Like ignored (already liked): announcementId={}, userId={}", announcementId, currentUser.getId());
        }
    }

    @Override
    @Transactional
    public void unlikeAnnouncement(Long campaignId, Long announcementId, User currentUser) {
        log.info("Processing unlike for announcement: campaignId={}, announcementId={}, userId={}", campaignId, announcementId, currentUser.getId());

        Announcement announcement = announcementAccessAuthorizer.requireAccessibleAnnouncement(campaignId, announcementId, currentUser);

        int affected = likeRepository.deleteLike(announcement.getId(), currentUser.getId());
        if (affected == 1) {
            announcementRepository.decrementLikesCount(announcement.getId());
            log.info("Announcement unliked successfully: announcementId={}, userId={}", announcementId, currentUser.getId());
        } else {
            log.debug("Unlike ignored (like not found): announcementId={}, userId={}", announcementId, currentUser.getId());
        }
    }
}
