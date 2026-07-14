package com.mgmtp.gives.security;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AnnouncementAccessAuthorizer {

    private final AnnouncementRepository announcementRepository;

    public void assertCanAccess(Announcement announcement, User user) {
        Campaign campaign = announcement.getCampaign();
        if (campaign == null) {
            throw new AppException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }

        CampaignStatus status = campaign.getStatus();
        boolean restricted = status == CampaignStatus.DRAFT
                || status == CampaignStatus.PENDING
                || status == CampaignStatus.REJECTED;

        if (restricted) {
            if (user == null) {
                throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS);
            }
            boolean isAdmin = user.getRole() == UserRole.ADMIN;
            boolean isCreator = campaign.getUser() != null
                    && Objects.equals(campaign.getUser().getId(), user.getId());

            if (!isAdmin && !isCreator) {
                throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS);
            }
        }
    }

    public Announcement requireAccessibleAnnouncement(Long campaignId, Long announcementId, User user) {
        Announcement announcement = announcementRepository.findByIdAndCampaignId(announcementId, campaignId)
                .orElseThrow(() -> new AppException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        assertCanAccess(announcement, user);
        return announcement;
    }
}
