package com.mgmtp.gives.service.meeting;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingRecipientResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignMeetingRecipientResolverTest {

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @InjectMocks
    private CampaignMeetingRecipientResolver resolver;

    @Test
    void resolveForCreationNotifyAllFiltersAdminsInactiveUsersAndDuplicateEmails() {
        User first = user(1L, "member@example.com", UserStatus.ACTIVE);
        User duplicate = user(2L, "MEMBER@example.com", UserStatus.ACTIVE);
        User admin = user(3L, "admin@example.com", UserStatus.ACTIVE);
        User inactive = user(4L, "inactive@example.com", UserStatus.INACTIVE);
        User missingEmail = user(5L, " ", UserStatus.ACTIVE);
        when(campaignMemberRepository.findByCampaignId(10L)).thenReturn(List.of(
                member(first, CampaignMemberRole.VOLUNTEER),
                member(duplicate, CampaignMemberRole.VOLUNTEER),
                member(admin, CampaignMemberRole.CAMPAIGN_ADMIN),
                member(inactive, CampaignMemberRole.VOLUNTEER),
                member(missingEmail, CampaignMemberRole.VOLUNTEER)));

        List<User> recipients = resolver.resolveForCreation(10L, true, null);

        assertEquals(List.of(first), recipients);
        List<CampaignMeetingRecipientResponse> responses = resolver.listSelectableRecipients(10L);
        assertEquals(1, responses.size());
        assertEquals(first.getId(), responses.getFirst().userId());
    }

    @Test
    void resolveForCreationSelectedPreservesUniqueSelectionAndValidatesMembership() {
        User first = user(1L, "first@example.com", UserStatus.ACTIVE);
        User second = user(2L, "second@example.com", UserStatus.ACTIVE);
        when(campaignMemberRepository.findByCampaignId(10L)).thenReturn(List.of(
                member(first, CampaignMemberRole.VOLUNTEER),
                member(second, CampaignMemberRole.VOLUNTEER)));

        assertEquals(List.of(second, first), resolver.resolveForCreation(10L, false, List.of(2L, 1L, 2L)));

        AppException exception = assertThrows(
                AppException.class,
                () -> resolver.resolveForCreation(10L, false, List.of(99L)));
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void resolveForCreationSelectedRejectsMissingOrUnreceivableRecipients() {
        AppException missing = assertThrows(
                AppException.class,
                () -> resolver.resolveForCreation(10L, false, List.of()));
        assertEquals(ErrorCode.VALIDATION_ERROR, missing.getErrorCode());

        User inactive = user(1L, "inactive@example.com", UserStatus.INACTIVE);
        when(campaignMemberRepository.findByCampaignId(10L))
                .thenReturn(List.of(member(inactive, CampaignMemberRole.VOLUNTEER)));

        AppException invalid = assertThrows(
                AppException.class,
                () -> resolver.resolveForCreation(10L, false, List.of(1L)));
        assertEquals(ErrorCode.VALIDATION_ERROR, invalid.getErrorCode());
    }

    @Test
    void invitationMetadataUsesStoredIdsAndCount() {
        Campaign campaign = new Campaign();
        campaign.setId(10L);
        User first = user(1L, "first@example.com", UserStatus.ACTIVE);
        User second = user(2L, "second@example.com", UserStatus.ACTIVE);
        when(campaignMemberRepository.findByCampaignId(10L)).thenReturn(List.of(
                member(first, CampaignMemberRole.VOLUNTEER),
                member(second, CampaignMemberRole.CAMPAIGN_ADMIN)));
        CampaignMeeting meeting = CampaignMeeting.builder()
                .campaign(campaign)
                .notifyAll(false)
                .invitedUserIds("2, 1, 2")
                .build();

        assertEquals(List.of(1L, 2L), resolver.invitedUserIds(meeting));
        assertEquals(2, resolver.invitedCount(meeting));
        assertEquals(2, resolver.listInvitedRecipients(meeting).size());

        meeting.setInvitedCount(7);
        assertEquals(7, resolver.invitedCount(meeting));
    }

    @Test
    void serializationSkipsNullsAndDuplicateUserIds() {
        User first = user(1L, "first@example.com", UserStatus.ACTIVE);
        User duplicate = user(1L, "duplicate@example.com", UserStatus.ACTIVE);
        User withoutId = user(null, "missing@example.com", UserStatus.ACTIVE);

        assertEquals("1", resolver.serializeUserIds(List.of(first, duplicate, withoutId)));
        assertNull(resolver.serializeUserIds(List.of()));
        assertNull(resolver.serializeUserIds(null));
    }

    private User user(Long id, String email, UserStatus status) {
        return User.builder()
                .id(id)
                .fullName("User " + id)
                .email(email)
                .status(status)
                .build();
    }

    private CampaignMember member(User user, CampaignMemberRole role) {
        return CampaignMember.builder()
                .user(user)
                .roleInCampaign(role)
                .build();
    }
}
