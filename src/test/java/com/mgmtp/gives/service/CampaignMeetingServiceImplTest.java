package com.mgmtp.gives.service;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.dto.campaign_meeting.CreateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.UpdateCampaignMeetingRequest;
import com.mgmtp.gives.dto.webex.WebexCreateMeetingCommand;
import com.mgmtp.gives.dto.webex.WebexMeetingResult;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import com.mgmtp.gives.enums.CampaignMeetingType;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMeetingRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.service.impl.CampaignMeetingServiceImpl;
import com.mgmtp.gives.util.CampaignAccessHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignMeetingServiceImplTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @Mock
    private CampaignMeetingRepository campaignMeetingRepository;

    @Mock
    private CampaignMediaRepository campaignMediaRepository;

    @Mock
    private WebexMeetingClient webexMeetingClient;

    @Mock
    private CampaignMeetingInvitationService campaignMeetingInvitationService;

    @Mock
    private MediaService mediaService;

    @Mock
    private UserWebexConnectionService userWebexConnectionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CampaignMeetingClock campaignMeetingClock;

    @Mock
    private CampaignAccessHelper campaignAccessHelper;

    @InjectMocks
    private CampaignMeetingServiceImpl campaignMeetingService;

    private Campaign campaign;
    private User systemAdmin;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);

        systemAdmin = new User();
        systemAdmin.setId(2L);
        systemAdmin.setRole(UserRole.ADMIN);

        campaign = new Campaign();
        campaign.setId(10L);
        campaign.setUser(owner);
        campaign.setStatus(CampaignStatus.APPROVED);

        when(campaignRepository.findById(campaign.getId())).thenReturn(Optional.of(campaign));
    }

    @Test
    void getMeetingRecipients_SystemAdminOfAnotherCampaign_ThrowsAppException() {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE))
                .when(campaignAccessHelper).validateCampaignAdmin(
                        eq(campaign.getId()),
                        eq(systemAdmin),
                        eq(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE));

        AppException exception = assertThrows(AppException.class,
                () -> campaignMeetingService.getMeetingRecipients(campaign.getId(), systemAdmin));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, exception.getErrorCode());
        verify(campaignMeetingRepository, never()).findByCampaignIdOrderByStartTimeAsc(campaign.getId());
    }

    @Test
    void getMeetings_SystemAdminWithoutCampaignMembership_ThrowsAppException() {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS))
                .when(campaignAccessHelper).validateCampaignMemberOrAdmin(
                        eq(campaign.getId()),
                        eq(systemAdmin),
                        eq(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS));

        AppException exception = assertThrows(AppException.class,
                () -> campaignMeetingService.getMeetings(campaign.getId(), "all", systemAdmin));

        assertEquals(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS, exception.getErrorCode());
        verify(campaignMeetingRepository, never()).findByCampaignIdOrderByStartTimeAsc(campaign.getId());
    }

    @Test
    void createMeeting_GeneratesStableCalendarUidAndSequenceZero() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime start = now.plusHours(2);
        LocalDateTime end = start.plusHours(1);
        when(campaignMeetingClock.now()).thenReturn(now);
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(userWebexConnectionService.getValidAccessToken(creator)).thenReturn("token");
        when(webexMeetingClient.createMeeting(any(), eq("token"))).thenReturn(new WebexMeetingResult(
                "webex-id", "https://webex.example/join", "Kickoff", "", "", "Asia/Bangkok", "active", "meeting"));
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> {
            CampaignMeeting meeting = invocation.getArgument(0);
            meeting.setId(100L);
            return meeting;
        });

        var response = campaignMeetingService.createMeeting(
                campaign.getId(),
                new CreateCampaignMeetingRequest(
                        "Kickoff",
                        "Description",
                        CampaignMeetingType.ONLINE,
                        null,
                        null,
                        start,
                        end,
                        true,
                        List.of()),
                creator);

        assertNotNull(response);
        ArgumentCaptor<CampaignMeeting> meetingCaptor = ArgumentCaptor.forClass(CampaignMeeting.class);
        verify(campaignMeetingRepository).save(meetingCaptor.capture());
        assertTrue(meetingCaptor.getValue().getCalendarUid().startsWith("campaign-meeting-"));
        assertTrue(meetingCaptor.getValue().getCalendarUid().endsWith("@mgmgives"));
        assertEquals(0, meetingCaptor.getValue().getCalendarSequence());
        verify(campaignMeetingInvitationService).sendInvitations(any(CampaignMeeting.class), eq(List.of(volunteer)));
    }

    @Test
    void createOnlineMeeting_TruncatesOnlyWebexAgendaWhenDescriptionExceedsWebexLimit() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime start = now.plusHours(2);
        LocalDateTime end = start.plusHours(1);
        String longDescription = "A".repeat(1_500);
        when(campaignMeetingClock.now()).thenReturn(now);
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(userWebexConnectionService.getValidAccessToken(creator)).thenReturn("token");
        when(webexMeetingClient.createMeeting(any(), eq("token"))).thenReturn(new WebexMeetingResult(
                "webex-id", "https://webex.example/join", "Kickoff", "", "", "Asia/Bangkok", "active", "meeting"));
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        campaignMeetingService.createMeeting(
                campaign.getId(),
                new CreateCampaignMeetingRequest(
                        "Kickoff",
                        longDescription,
                        CampaignMeetingType.ONLINE,
                        null,
                        null,
                        start,
                        end,
                        true,
                        List.of()),
                creator);

        ArgumentCaptor<WebexCreateMeetingCommand> webexCommandCaptor =
                ArgumentCaptor.forClass(WebexCreateMeetingCommand.class);
        verify(webexMeetingClient).createMeeting(webexCommandCaptor.capture(), eq("token"));
        assertEquals(1300, webexCommandCaptor.getValue().description().length());
        assertTrue(webexCommandCaptor.getValue().description().endsWith("..."));

        ArgumentCaptor<CampaignMeeting> meetingCaptor = ArgumentCaptor.forClass(CampaignMeeting.class);
        verify(campaignMeetingRepository).save(meetingCaptor.capture());
        assertEquals(longDescription, meetingCaptor.getValue().getDescription());
    }

    @Test
    void createOnlineMeeting_SendsPlainTextAgendaToWebexWhenDescriptionContainsHtml() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime start = now.plusHours(2);
        LocalDateTime end = start.plusHours(1);
        String htmlDescription = """
                &lt;p&gt;Hello mgmies!&lt;/p&gt;
                &lt;p&gt;Let’s gather and support &lt;strong&gt;mgmGives&lt;/strong&gt;.&lt;/p&gt;
                &lt;p&gt;&lt;a href="https://example.com"&gt;Join meeting&lt;/a&gt;&lt;/p&gt;
                """;
        when(campaignMeetingClock.now()).thenReturn(now);
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(userWebexConnectionService.getValidAccessToken(creator)).thenReturn("token");
        when(webexMeetingClient.createMeeting(any(), eq("token"))).thenReturn(new WebexMeetingResult(
                "webex-id", "https://webex.example/join", "Kickoff", "", "", "Asia/Bangkok", "active", "meeting"));
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        campaignMeetingService.createMeeting(
                campaign.getId(),
                new CreateCampaignMeetingRequest(
                        "Kickoff",
                        htmlDescription,
                        CampaignMeetingType.ONLINE,
                        null,
                        null,
                        start,
                        end,
                        true,
                        List.of()),
                creator);

        ArgumentCaptor<WebexCreateMeetingCommand> webexCommandCaptor =
                ArgumentCaptor.forClass(WebexCreateMeetingCommand.class);
        verify(webexMeetingClient).createMeeting(webexCommandCaptor.capture(), eq("token"));
        String agenda = webexCommandCaptor.getValue().description();
        assertTrue(agenda.contains("Hello mgmies!"));
        assertTrue(agenda.contains("Let’s gather and support mgmGives."));
        assertTrue(agenda.contains("Join meeting"));
        assertTrue(!agenda.contains("<p>"));
        assertTrue(!agenda.contains("<strong>"));
        assertTrue(!agenda.contains("<a "));

        ArgumentCaptor<CampaignMeeting> meetingCaptor = ArgumentCaptor.forClass(CampaignMeeting.class);
        verify(campaignMeetingRepository).save(meetingCaptor.capture());
        assertEquals(htmlDescription, meetingCaptor.getValue().getDescription());
    }

    @Test
    void updateMeeting_IncrementsCalendarSequenceAndSendsRequestToExistingRecipients() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.ONLINE, CampaignMeetingStatus.UPCOMING, 0);
        meeting.setNotifyAll(false);
        meeting.setInvitedUserIds("3");
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 8, 0));
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(userWebexConnectionService.getValidAccessToken(creator)).thenReturn("token");
        when(webexMeetingClient.updateMeeting(eq("webex-id"), any(), eq("token"))).thenReturn(new WebexMeetingResult(
                "webex-id", "https://webex.example/updated", "Updated", "", "", "Asia/Bangkok", "active", "meeting"));
        when(campaignMeetingRepository.existsOverlappingMeeting(any(), any(), any(), any(), any())).thenReturn(false);
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        campaignMeetingService.updateMeeting(
                campaign.getId(),
                meeting.getId(),
                new UpdateCampaignMeetingRequest(
                        "Updated",
                        "Updated description",
                        null,
                        null,
                        meeting.getStartTime().plusHours(1),
                        meeting.getEndTime().plusHours(1),
                        null,
                        null),
                creator);

        assertEquals(1, meeting.getCalendarSequence());
        assertEquals("calendar-uid", meeting.getCalendarUid());
        verify(campaignMeetingInvitationService).sendInvitations(meeting, List.of(volunteer));
    }

    @Test
    void updateOnlineMeeting_TruncatesOnlyWebexAgendaWhenDescriptionExceedsWebexLimit() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.ONLINE, CampaignMeetingStatus.UPCOMING, 0);
        meeting.setNotifyAll(false);
        meeting.setInvitedUserIds("3");
        String longDescription = "B".repeat(1_500);
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 8, 0));
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(userWebexConnectionService.getValidAccessToken(creator)).thenReturn("token");
        when(webexMeetingClient.updateMeeting(eq("webex-id"), any(), eq("token"))).thenReturn(new WebexMeetingResult(
                "webex-id", "https://webex.example/updated", "Updated", "", "", "Asia/Bangkok", "active", "meeting"));
        when(campaignMeetingRepository.existsOverlappingMeeting(any(), any(), any(), any(), any())).thenReturn(false);
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        campaignMeetingService.updateMeeting(
                campaign.getId(),
                meeting.getId(),
                new UpdateCampaignMeetingRequest(
                        "Updated",
                        longDescription,
                        null,
                        null,
                        meeting.getStartTime().plusHours(1),
                        meeting.getEndTime().plusHours(1),
                        null,
                        null),
                creator);

        ArgumentCaptor<WebexCreateMeetingCommand> webexCommandCaptor =
                ArgumentCaptor.forClass(WebexCreateMeetingCommand.class);
        verify(webexMeetingClient).updateMeeting(eq("webex-id"), webexCommandCaptor.capture(), eq("token"));
        assertEquals(1300, webexCommandCaptor.getValue().description().length());
        assertTrue(webexCommandCaptor.getValue().description().endsWith("..."));
        assertEquals(longDescription, meeting.getDescription());
    }

    @Test
    void updateMeeting_OnlineToOffline_UpdatesTypeAndClearsWebexDetails() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.ONLINE, CampaignMeetingStatus.UPCOMING, 0);
        meeting.setNotifyAll(false);
        meeting.setInvitedUserIds("3");
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 8, 0));
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(campaignMeetingRepository.existsOverlappingMeeting(any(), any(), any(), any(), any())).thenReturn(false);
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = campaignMeetingService.updateMeeting(
                campaign.getId(),
                meeting.getId(),
                new UpdateCampaignMeetingRequest(
                        "Offline updated",
                        "Updated description",
                        CampaignMeetingType.OFFLINE,
                        "Office",
                        meeting.getStartTime().plusHours(1),
                        meeting.getEndTime().plusHours(1),
                        null,
                        null),
                creator);

        assertEquals(CampaignMeetingType.OFFLINE, meeting.getMeetingType());
        assertEquals(CampaignMeetingType.OFFLINE, response.meetingType());
        assertEquals("Office", meeting.getLocation());
        assertEquals(null, meeting.getWebexMeetingId());
        assertEquals(null, meeting.getMeetingUrl());
        verify(campaignMeetingInvitationService).sendInvitations(meeting, List.of(volunteer));
    }

    @Test
    void cancelMeeting_IncrementsCalendarSequenceAndSendsCancelNotice() {
        User creator = activeUser(1L, "Host", "host@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.OFFLINE, CampaignMeetingStatus.UPCOMING, 1);
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 8, 0));
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

        campaignMeetingService.cancelMeeting(campaign.getId(), meeting.getId(), creator);

        assertEquals(2, meeting.getCalendarSequence());
        assertEquals(CampaignMeetingStatus.CANCELLED, meeting.getStatus());
        verify(campaignMeetingInvitationService).sendCancellationNotice(meeting);
    }

    @Test
    void updateMeetingStatus_OfflineMeetingWithinTimeWindow_BecomesInProgressWithoutWebexCall() {
        User creator = activeUser(1L, "Host", "host@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.OFFLINE, CampaignMeetingStatus.UPCOMING, 0);
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 10, 30));

        var response = campaignMeetingService.updateMeetingStatus(campaign.getId(), meeting.getId(), creator);

        assertEquals(CampaignMeetingStatus.IN_PROGRESS, meeting.getStatus());
        assertEquals(CampaignMeetingStatus.IN_PROGRESS, response.status());
        verify(webexMeetingClient, never()).getMeeting(any(), any());
    }

    @Test
    void updateMeetingStatus_OfflineMeetingAfterEndTime_BecomesEndedWithoutWebexCall() {
        User creator = activeUser(1L, "Host", "host@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.OFFLINE, CampaignMeetingStatus.IN_PROGRESS, 0);
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 11, 0));

        var response = campaignMeetingService.updateMeetingStatus(campaign.getId(), meeting.getId(), creator);

        assertEquals(CampaignMeetingStatus.ENDED, meeting.getStatus());
        assertEquals(CampaignMeetingStatus.ENDED, response.status());
        verify(webexMeetingClient, never()).getMeeting(any(), any());
    }

    @Test
    void getMeeting_HybridMeetingStartedButStoredUpcoming_UsesStoredStatusForActions() {
        User creator = activeUser(1L, "Host", "host@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.HYBRID, CampaignMeetingStatus.UPCOMING, 0);
        meeting.setWebexMeetingId("webex-id");
        meeting.setMeetingUrl("https://webex.example/join");
        meeting.setLocation("Cafe");
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 10, 30));
        when(campaignAccessHelper.isCampaignAdmin(campaign.getId(), creator)).thenReturn(true);

        var response = campaignMeetingService.getMeeting(campaign.getId(), meeting.getId(), creator);

        assertEquals(CampaignMeetingStatus.UPCOMING, response.status());
        assertEquals(CampaignMeetingStatus.UPCOMING.name(), response.displayStatus());
        assertEquals(true, response.canUpdate());
        assertEquals(true, response.canCancel());
    }

    @Test
    void getMeeting_OfflineMeetingStartedButStoredUpcoming_DisplaysInProgressAndDisablesUpcomingActions() {
        User creator = activeUser(1L, "Host", "host@example.com");
        CampaignMeeting meeting = meeting(creator, CampaignMeetingType.OFFLINE, CampaignMeetingStatus.UPCOMING, 0);
        when(campaignMeetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(campaignMeetingClock.now()).thenReturn(LocalDateTime.of(2026, 1, 1, 10, 30));
        when(campaignAccessHelper.isCampaignAdmin(campaign.getId(), creator)).thenReturn(true);

        var response = campaignMeetingService.getMeeting(campaign.getId(), meeting.getId(), creator);

        assertEquals(CampaignMeetingStatus.UPCOMING, response.status());
        assertEquals(CampaignMeetingStatus.IN_PROGRESS.name(), response.displayStatus());
        assertEquals(false, response.canUpdate());
        assertEquals(false, response.canCancel());
    }

    @Test
    void createOnlineMeeting_WithManualUrl_DoesNotCreateWebexMeeting() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime start = now.plusHours(2);
        LocalDateTime end = start.plusHours(1);
        when(campaignMeetingClock.now()).thenReturn(now);
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> {
            CampaignMeeting meeting = invocation.getArgument(0);
            meeting.setId(101L);
            return meeting;
        });

        var response = campaignMeetingService.createMeeting(
                campaign.getId(),
                new CreateCampaignMeetingRequest(
                        "Manual online",
                        "Description",
                        CampaignMeetingType.ONLINE,
                        null,
                        "https://example.webex.com/meeting-link",
                        start,
                        end,
                        true,
                        List.of()),
                creator);

        assertEquals("https://example.webex.com/meeting-link", response.meetingUrl());
        verify(userWebexConnectionService, never()).getValidAccessToken(creator);
        verify(webexMeetingClient, never()).createMeeting(any(), any());
    }

    @Test
    void createHybridMeeting_WithRoomAndManualUrl_SavesHybridWithoutCreatingWebexMeeting() {
        User creator = activeUser(1L, "Host", "host@example.com");
        User volunteer = activeUser(3L, "Volunteer", "volunteer@example.com");
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime start = now.plusHours(2);
        LocalDateTime end = start.plusHours(1);
        when(campaignMeetingClock.now()).thenReturn(now);
        when(campaignMemberRepository.findByCampaignId(campaign.getId()))
                .thenReturn(List.of(member(volunteer, CampaignMemberRole.VOLUNTEER)));
        when(campaignMeetingRepository.save(any(CampaignMeeting.class))).thenAnswer(invocation -> {
            CampaignMeeting meeting = invocation.getArgument(0);
            meeting.setId(102L);
            return meeting;
        });

        var response = campaignMeetingService.createMeeting(
                campaign.getId(),
                new CreateCampaignMeetingRequest(
                        "Hybrid meeting",
                        "Description",
                        CampaignMeetingType.HYBRID,
                        "Meetingroom DA NANG QT 1st floor Blue",
                        "https://example.webex.com/meeting-link",
                        start,
                        end,
                        true,
                        List.of()),
                creator);

        assertEquals(CampaignMeetingType.HYBRID, response.meetingType());
        assertEquals("Meetingroom DA NANG QT 1st floor Blue", response.location());
        assertEquals("https://example.webex.com/meeting-link", response.meetingUrl());
        verify(userWebexConnectionService, never()).getValidAccessToken(creator);
        verify(webexMeetingClient, never()).createMeeting(any(), any());
    }

    private User activeUser(Long id, String fullName, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private CampaignMember member(User user, CampaignMemberRole role) {
        CampaignMember member = new CampaignMember();
        member.setCampaign(campaign);
        member.setUser(user);
        member.setRoleInCampaign(role);
        return member;
    }

    private CampaignMeeting meeting(
            User creator,
            CampaignMeetingType meetingType,
            CampaignMeetingStatus status,
            int sequence
    ) {
        CampaignMeeting meeting = new CampaignMeeting();
        meeting.setId(200L);
        meeting.setCampaign(campaign);
        meeting.setCreatedBy(creator);
        meeting.setWebexMeetingId(meetingType == CampaignMeetingType.ONLINE ? "webex-id" : null);
        meeting.setCalendarUid("calendar-uid");
        meeting.setCalendarSequence(sequence);
        meeting.setTitle("Kickoff");
        meeting.setDescription("Description");
        meeting.setMeetingType(meetingType);
        meeting.setMeetingUrl(meetingType == CampaignMeetingType.ONLINE ? "https://webex.example/join" : null);
        meeting.setLocation(meetingType == CampaignMeetingType.OFFLINE ? "Office" : null);
        meeting.setStartTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        meeting.setEndTime(LocalDateTime.of(2026, 1, 1, 11, 0));
        meeting.setStatus(status);
        return meeting;
    }
}
