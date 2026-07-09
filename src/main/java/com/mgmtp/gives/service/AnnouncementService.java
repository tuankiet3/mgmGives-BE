package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.announcement.AnnouncementResponse;
import com.mgmtp.gives.dto.announcement.AudienceFilter;
import com.mgmtp.gives.dto.announcement.CreateAnnouncementRequest;
import com.mgmtp.gives.dto.announcement.UpdateAnnouncementRequest;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface AnnouncementService {
    AnnouncementResponse createAnnouncement(Long campaignId, CreateAnnouncementRequest request, User currentUser);

    AnnouncementResponse updateAnnouncement(Long campaignId, Long announcementId, UpdateAnnouncementRequest request, User currentUser);

    void deleteAnnouncement(Long campaignId, Long announcementId, User currentUser);

    Page<AnnouncementResponse> getAnnouncementsByCampaign(Long campaignId, Pageable pageable);

    AnnouncementResponse getAnnouncementById(Long campaignId, Long announcementId);

    void validateCampaignOwnership(Campaign campaign, User user);

    String sanitizeContent(String content);

    Set<NotificationRecipient> resolveAudience(Campaign campaign, AudienceFilter filter, User publisher);
}
