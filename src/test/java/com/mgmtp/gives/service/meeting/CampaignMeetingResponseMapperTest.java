package com.mgmtp.gives.service.meeting;

import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingResponse;
import com.mgmtp.gives.dto.campaign_meeting.MeetingActivityResponse;
import com.mgmtp.gives.dto.campaign_meeting.MeetingNotesResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.service.CampaignMeetingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignMeetingResponseMapperTest {

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @Mock
    private CampaignMeetingRecipientResolver recipientResolver;

    @Mock
    private CampaignMeetingClock campaignMeetingClock;

    @InjectMocks
    private CampaignMeetingResponseMapper mapper;

    private LocalDateTime now;
    private User owner;
    private Campaign campaign;
    private CampaignMeeting meeting;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.of(2030, 1, 1, 8, 0);
        owner = user(1L, "Ada Lovelace", "ada@example.com");
        campaign = Campaign.builder().id(10L).user(owner).build();
        meeting = CampaignMeeting.builder()
                .campaign(campaign)
                .createdBy(owner)
                .webexMeetingId("webex-11")
                .title("Planning")
                .description("Launch plan")
                .meetingUrl("https://meet.example.com/room")
                .notifyAll(false)
                .startTime(now.plusHours(1))
                .endTime(now.plusHours(2))
                .status(CampaignMeetingStatus.UPCOMING)
                .updatedAt(now)
                .build();
        meeting.setId(11L);
        meeting.setCreatedAt(now.minusHours(1));
    }

    @Test
    void toResponseExposesManagementFieldsForCampaignOwner() {
        when(campaignMeetingClock.now()).thenReturn(now);
        when(recipientResolver.invitedCount(meeting)).thenReturn(3);
        when(recipientResolver.invitedUserIds(meeting)).thenReturn(List.of(7L, 8L));

        CampaignMeetingResponse response = mapper.toResponse(meeting, owner);

        assertEquals(11L, response.id());
        assertEquals("UPCOMING", response.displayStatus());
        assertEquals(3, response.invitedCount());
        assertEquals(List.of(7L, 8L), response.invitedUserIds());
        assertTrue(response.canManage());
        assertTrue(response.canUpdate());
        assertTrue(response.canCancel());
        assertTrue(response.canEditNotes());
    }

    @Test
    void toResponseHidesRecipientIdsFromRegularViewer() {
        User viewer = user(2L, "Viewer", "viewer@example.com");
        when(campaignMeetingClock.now()).thenReturn(now);
        when(campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                10L, 2L, CampaignMemberRole.CAMPAIGN_ADMIN)).thenReturn(false);
        when(recipientResolver.invitedCount(meeting)).thenReturn(3);

        CampaignMeetingResponse response = mapper.toResponse(meeting, viewer);

        assertFalse(response.canManage());
        assertFalse(response.canUpdate());
        assertNull(response.invitedUserIds());
        verify(recipientResolver, never()).invitedUserIds(meeting);
    }

    @Test
    void toActivityResponsesSortsPresentLifecycleEvents() {
        User editor = user(2L, "", "editor@example.com");
        meeting.setInvitationsSentAt(now.minusMinutes(45));
        meeting.setUpdatedBy(editor);
        meeting.setUpdatedAt(now.minusMinutes(30));
        meeting.setCancelledAt(now.minusMinutes(15));
        meeting.setCancelledBy(null);
        meeting.setNotesUpdatedAt(now);
        meeting.setNotesUpdatedBy(editor);
        when(recipientResolver.invitedCount(meeting)).thenReturn(4);

        List<MeetingActivityResponse> activities = mapper.toActivityResponses(meeting);

        assertEquals(
                List.of("CREATED", "INVITATIONS_SENT", "UPDATED", "CANCELLED", "NOTES_UPDATED"),
                activities.stream().map(MeetingActivityResponse::type).toList());
        assertEquals("editor@example.com", activities.get(2).actorName());
        assertEquals("Unknown", activities.get(3).actorName());
        assertEquals("Invitations were sent to 4 members", activities.get(1).message());
    }

    @Test
    void mapsNotesAndMediaMetadata() {
        User editor = user(2L, "Grace Hopper", "grace@example.com");
        meeting.setNotes("Decisions");
        meeting.setNotesUpdatedAt(now);
        meeting.setNotesUpdatedBy(editor);

        MeetingNotesResponse notes = mapper.toNotesResponse(meeting, true);
        assertEquals("Decisions", notes.content());
        assertEquals("Grace Hopper", notes.updatedByName());
        assertTrue(notes.canEdit());

        CampaignMedia media = CampaignMedia.builder()
                .url("/media/brief.png")
                .mediaType("IMAGE")
                .isCover(false)
                .caption("Brief")
                .displayOrder(2)
                .context(MediaContext.CAMPAIGN)
                .build();
        media.setId(22L);
        CampaignMediaResponse response = mapper.toMediaResponse(media);

        assertEquals(22L, response.getId());
        assertEquals("CAMPAIGN", response.getContext());
        assertEquals("Brief", response.getCaption());
    }

    private User user(Long id, String fullName, String email) {
        return User.builder()
                .id(id)
                .fullName(fullName)
                .email(email)
                .build();
    }
}
