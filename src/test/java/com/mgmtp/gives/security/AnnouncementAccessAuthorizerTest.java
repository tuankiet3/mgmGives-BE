package com.mgmtp.gives.security;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnouncementAccessAuthorizerTest {

    private final AnnouncementAccessAuthorizer authorizer = new AnnouncementAccessAuthorizer(null);

    private Announcement announcement;
    private User creator;

    @BeforeEach
    void setUp() {
        creator = user(1L, UserRole.USER);
        Campaign campaign = new Campaign();
        campaign.setId(10L);
        campaign.setUser(creator);
        campaign.setStatus(CampaignStatus.DRAFT);

        announcement = new Announcement();
        announcement.setId(100L);
        announcement.setCampaign(campaign);
    }

    @Test
    void assertCanAccess_RejectsRegularUserForDraftCampaign() {
        AppException exception = assertThrows(AppException.class,
                () -> authorizer.assertCanAccess(announcement, user(2L, UserRole.USER)));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
    }

    @Test
    void assertCanAccess_AllowsCreatorAndAdminForDraftCampaign() {
        assertDoesNotThrow(() -> authorizer.assertCanAccess(announcement, creator));
        assertDoesNotThrow(() -> authorizer.assertCanAccess(announcement, user(3L, UserRole.ADMIN)));
    }

    @Test
    void assertCanAccess_RejectsNullUserForDraftCampaign() {
        AppException exception = assertThrows(AppException.class,
                () -> authorizer.assertCanAccess(announcement, null));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
    }

    @Test
    void assertCanAccess_AllowsNullUserForActiveCampaign() {
        announcement.getCampaign().setStatus(CampaignStatus.APPROVED);
        assertDoesNotThrow(() -> authorizer.assertCanAccess(announcement, null));
    }

    private static User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
