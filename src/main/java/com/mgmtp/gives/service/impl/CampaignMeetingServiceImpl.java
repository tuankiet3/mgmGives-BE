package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingRecipientResponse;
import com.mgmtp.gives.dto.campaign_meeting.CreateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.MeetingActivityResponse;
import com.mgmtp.gives.dto.campaign_meeting.MeetingNotesResponse;
import com.mgmtp.gives.dto.campaign_meeting.UpdateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.UpdateMeetingNotesRequest;
import com.mgmtp.gives.dto.webex.WebexCreateMeetingCommand;
import com.mgmtp.gives.dto.webex.WebexMeetingResponse;
import com.mgmtp.gives.dto.webex.WebexMeetingResult;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.*;
import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingWebexCancellationEvent;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignMeetingRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.util.CampaignAccessHelper;
import com.mgmtp.gives.service.CampaignMeetingClock;
import com.mgmtp.gives.service.CampaignMeetingInvitationService;
import com.mgmtp.gives.service.CampaignMeetingService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.service.UserWebexConnectionService;
import com.mgmtp.gives.service.WebexMeetingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignMeetingServiceImpl implements CampaignMeetingService {
    private static final int WEBEX_AGENDA_MAX_LENGTH = 1300;
    private static final String TRUNCATED_AGENDA_SUFFIX = "...";

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMeetingRepository campaignMeetingRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final WebexMeetingClient webexMeetingClient;
    private final CampaignMeetingInvitationService campaignMeetingInvitationService;
    private final MediaService mediaService;
    private final UserWebexConnectionService userWebexConnectionService;
    private final ApplicationEventPublisher eventPublisher;
    private final CampaignMeetingClock campaignMeetingClock;
    private final CampaignAccessHelper campaignAccessHelper;

    @Override
    @Transactional
    public CampaignMeetingResponse createMeeting(Long campaignId, CreateCampaignMeetingRequest request,
            User currentUser) {
        Campaign campaign = getCampaign(campaignId);

        validateMeetingTime(request.startTime(), request.endTime());
        validateCampaignCanHaveMeetings(campaign);
        requireCampaignAdmin(campaign, currentUser);
        validateMeetingTimeConflict(campaign.getId(), null, request.startTime(), request.endTime());
        CampaignMeetingType meetingType = request.meetingType() == null ? CampaignMeetingType.ONLINE : request.meetingType();
        String location = normalizeLocation(request.location());
        String manualMeetingUrl = normalizeMeetingUrl(request.meetingUrl());
        validateMeetingTypeDetails(meetingType, location, manualMeetingUrl);
        boolean notifyAll = request.notifyAllMembers() == null || request.notifyAllMembers();
        List<User> recipients = resolveRecipients(campaign.getId(), notifyAll, request.recipientUserIds());
        WebexMeetingResult webexMeeting = null;
        if (requiresOnlineMeeting(meetingType) && !StringUtils.hasText(manualMeetingUrl)) {
            String accessToken = userWebexConnectionService.getValidAccessToken(currentUser);
            webexMeeting = webexMeetingClient.createMeeting(new WebexCreateMeetingCommand(
                    request.title(),
                    webexAgenda(request.description()),
                    request.startTime(),
                    request.endTime()), accessToken);
        }

        CampaignMeeting meeting = CampaignMeeting.builder()
                .campaign(campaign)
                .createdBy(currentUser)
                .webexMeetingId(webexMeeting != null ? webexMeeting.id() : null)
                .calendarUid(generateCalendarUid())
                .calendarSequence(0)
                .title(request.title())
                .description(request.description())
                .meetingUrl(webexMeeting != null ? webexMeeting.webLink() : manualMeetingUrl)
                .meetingType(meetingType)
                .location(location)
                .notifyAll(notifyAll)
                .invitedUserIds(serializeUserIds(recipients))
                .invitedCount(recipients.size())
                .invitationsSentAt(campaignMeetingClock.now())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(CampaignMeetingStatus.UPCOMING)
                .updatedAt(campaignMeetingClock.now())
                .build();

        CampaignMeeting saved = campaignMeetingRepository.save(meeting);
        log.info("Campaign meeting created: meetingId={}, campaignId={}, userId={}",
                saved.getId(), campaignId, currentUser.getId());
        campaignMeetingInvitationService.sendInvitations(saved, recipients);

        return toResponse(saved, currentUser);
    }

    @Override
    @Transactional
    public List<CampaignMeetingResponse> getMeetings(Long campaignId, String view, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);

        LocalDateTime now = campaignMeetingClock.now();
        syncScheduledMeetingStatuses(now);
        return campaignMeetingRepository.findByCampaignIdOrderByStartTimeAsc(campaignId)
                .stream()
                .filter(meeting -> matchesView(meeting, view, now))
                .sorted(meetingComparator(view))
                .map(meeting -> toResponse(meeting, currentUser))
                .toList();
    }

    @Override
    @Transactional
    public CampaignMeetingResponse getMeeting(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);
        syncScheduledMeetingStatuses(campaignMeetingClock.now());
        return toResponse(getMeetingInCampaign(campaignId, meetingId), currentUser);
    }

    @Override
    @Transactional
    public CampaignMeetingResponse updateMeeting(
            Long campaignId,
            Long meetingId,
            UpdateCampaignMeetingRequest request,
            User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);

        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);
        validateMeetingIsUpcomingForUpdate(meeting);
        validateRecipientsImmutable(request);

        String title = request.title() != null ? request.title().trim() : meeting.getTitle();
        String description = request.description() != null ? request.description() : meeting.getDescription();
        CampaignMeetingType meetingType = request.meetingType() != null ? request.meetingType() : meetingType(meeting);
        LocalDateTime startTime = request.startTime() != null ? request.startTime() : meeting.getStartTime();
        LocalDateTime endTime = request.endTime() != null ? request.endTime() : meeting.getEndTime();
        String location = requiresRoom(meetingType)
                ? (request.location() != null ? normalizeLocation(request.location()) : meeting.getLocation())
                : null;
        String meetingUrl = requiresOnlineMeeting(meetingType) ? meeting.getMeetingUrl() : null;
        String webexMeetingId = requiresOnlineMeeting(meetingType) ? meeting.getWebexMeetingId() : null;

        if (!StringUtils.hasText(title)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Title must not be blank");
        }
        validateMeetingTypeDetails(meetingType, location, meetingUrl);
        validateMeetingTime(startTime, endTime);
        validateMeetingTimeConflict(campaignId, meetingId, startTime, endTime);

        WebexMeetingResult webexMeeting = null;
        if (requiresOnlineMeeting(meetingType)) {
            User hostUser = meeting.getCreatedBy();
            if (StringUtils.hasText(webexMeetingId)) {
                String accessToken = userWebexConnectionService.getValidAccessToken(hostUser);
                webexMeeting = webexMeetingClient.updateMeeting(
                        webexMeetingId,
                        new WebexCreateMeetingCommand(title, webexAgenda(description), startTime, endTime),
                        accessToken);
            } else if (!StringUtils.hasText(meetingUrl)) {
                String accessToken = userWebexConnectionService.getValidAccessToken(hostUser);
                webexMeeting = webexMeetingClient.createMeeting(
                        new WebexCreateMeetingCommand(title, webexAgenda(description), startTime, endTime),
                        accessToken);
            }
        } else if (StringUtils.hasText(meeting.getWebexMeetingId())) {
            User hostUser = meeting.getCreatedBy();
            eventPublisher.publishEvent(new CampaignMeetingWebexCancellationEvent(
                    meeting.getId(),
                    meeting.getWebexMeetingId(),
                    hostUser != null ? hostUser.getId() : null));
        }

        meeting.setTitle(title);
        meeting.setDescription(description);
        meeting.setStartTime(startTime);
        meeting.setEndTime(endTime);
        meeting.setMeetingType(meetingType);
        meeting.setLocation(location);
        if (webexMeeting != null) {
            meeting.setWebexMeetingId(webexMeeting.id());
            meeting.setMeetingUrl(webexMeeting.webLink());
        } else {
            meeting.setWebexMeetingId(webexMeetingId);
            meeting.setMeetingUrl(meetingUrl);
        }
        meeting.setCalendarSequence(nextCalendarSequence(meeting));
        meeting.setUpdatedAt(campaignMeetingClock.now());
        meeting.setUpdatedBy(currentUser);

        CampaignMeeting saved = campaignMeetingRepository.save(meeting);
        log.info("Campaign meeting updated: meetingId={}, campaignId={}, userId={}",
                meetingId, campaignId, currentUser.getId());
        campaignMeetingInvitationService.sendInvitations(saved, users(resolveInvitedMembers(saved)));
        return toResponse(saved, currentUser);
    }

    @Override
    @Transactional
    public CampaignMeetingResponse updateMeetingStatus(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);

        CampaignMeeting campaignMeeting = getMeetingInCampaign(campaignId, meetingId);
        if (meetingType(campaignMeeting) == CampaignMeetingType.OFFLINE) {
            updateScheduledMeetingStatus(campaignMeeting, currentUser);
            return toResponse(campaignMeeting, currentUser);
        }
        if (!StringUtils.hasText(campaignMeeting.getWebexMeetingId())) {
            return toResponse(campaignMeeting, currentUser);
        }
        User hostUser = campaignMeeting.getCreatedBy();

        String accessToken = userWebexConnectionService.getValidAccessToken(hostUser);

        WebexMeetingResult response = webexMeetingClient.getMeeting(campaignMeeting.getWebexMeetingId(), accessToken);

        CampaignMeetingStatus currentStatus = campaignMeeting.getStatus();
        CampaignMeetingStatus newStatus = mapStatus(response.state(), currentStatus);

        if (isEndedLiveSession(response.state(), currentStatus, newStatus)) {
            webexMeetingClient.cancelMeeting(campaignMeeting.getWebexMeetingId(), accessToken);
            log.info(
                    "Cancelled Webex meeting after host ended live session. meetingId={}, campaignId={}, webexMeetingId={}",
                    meetingId, campaignId, campaignMeeting.getWebexMeetingId());
        }

        if (currentStatus != newStatus) {
            campaignMeeting.setStatus(newStatus);
            campaignMeeting.setUpdatedAt(campaignMeetingClock.now());
            campaignMeeting.setUpdatedBy(currentUser);
        }

        return toResponse(campaignMeeting, currentUser);
    }

    private void updateScheduledMeetingStatus(CampaignMeeting meeting, User currentUser) {
        CampaignMeetingStatus currentStatus = meeting.getStatus();
        CampaignMeetingStatus newStatus = mapScheduledStatus(meeting, campaignMeetingClock.now());

        if (currentStatus != newStatus) {
            meeting.setStatus(newStatus);
            meeting.setUpdatedAt(campaignMeetingClock.now());
            meeting.setUpdatedBy(currentUser);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMeetingRecipientResponse> getMeetingRecipients(Long campaignId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);

        return resolveAllRecipients(campaignId)
                .stream()
                .filter(member -> !member.getRoleInCampaign().equals(CampaignMemberRole.CAMPAIGN_ADMIN))
                .map(member -> new CampaignMeetingRecipientResponse(
                        member.getUser().getId(),
                        member.getUser().getFullName(),
                        member.getUser().getEmail(),
                        member.getRoleInCampaign()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMeetingRecipientResponse> getInvitedMembers(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);
        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);

        List<CampaignMember> invitedMembers = resolveInvitedMembers(meeting);

        return invitedMembers.stream()
                .map(member -> new CampaignMeetingRecipientResponse(
                        member.getUser().getId(),
                        member.getUser().getFullName(),
                        member.getUser().getEmail(),
                        member.getRoleInCampaign()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingNotesResponse getMeetingNotes(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);
        return toNotesResponse(getMeetingInCampaign(campaignId, meetingId), canManageMeeting(campaign, currentUser));
    }

    @Override
    @Transactional
    public MeetingNotesResponse updateMeetingNotes(
            Long campaignId,
            Long meetingId,
            UpdateMeetingNotesRequest request,
            User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);
        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);

        LocalDateTime now = campaignMeetingClock.now();
        meeting.setNotes(request.content());
        meeting.setNotesUpdatedAt(now);
        meeting.setNotesUpdatedBy(currentUser);
        meeting.setUpdatedAt(now);
        meeting.setUpdatedBy(currentUser);

        return toNotesResponse(campaignMeetingRepository.save(meeting), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetingActivityResponse> getMeetingActivity(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);
        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);

        List<MeetingActivityResponse> activities = new ArrayList<>();
        activities.add(new MeetingActivityResponse(
                "CREATED",
                "Meeting was created",
                meeting.getCreatedBy() != null ? meeting.getCreatedBy().getId() : null,
                actorName(meeting.getCreatedBy(), "System"),
                meeting.getCreatedAt()));
        if (meeting.getInvitationsSentAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "INVITATIONS_SENT",
                    "Invitations were sent to " + invitedCount(meeting) + " members",
                    null,
                    "System",
                    meeting.getInvitationsSentAt()));
        }
        if (meeting.getUpdatedBy() != null && meeting.getUpdatedAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "UPDATED",
                    "Meeting was updated",
                    meeting.getUpdatedBy().getId(),
                    actorName(meeting.getUpdatedBy(), "Unknown"),
                    meeting.getUpdatedAt()));
        }
        if (meeting.getCancelledAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "CANCELLED",
                    "Meeting was cancelled",
                    meeting.getCancelledBy() != null ? meeting.getCancelledBy().getId() : null,
                    actorName(meeting.getCancelledBy(), "Unknown"),
                    meeting.getCancelledAt()));
        }
        if (meeting.getNotesUpdatedAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "NOTES_UPDATED",
                    "Meeting notes were updated",
                    meeting.getNotesUpdatedBy() != null ? meeting.getNotesUpdatedBy().getId() : null,
                    actorName(meeting.getNotesUpdatedBy(), "Unknown"),
                    meeting.getNotesUpdatedAt()));
        }
        return activities.stream()
                .filter(activity -> activity.timestamp() != null)
                .sorted(Comparator.comparing(MeetingActivityResponse::timestamp))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMediaResponse> getMeetingAttachments(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);
        getMeetingInCampaign(campaignId, meetingId);

        return campaignMediaRepository.findByCampaignIdAndMeetingIdAndDeletedAtIsNull(campaignId, meetingId)
                .stream()
                .map(this::toMediaResponse)
                .toList();
    }

    @Override
    @Transactional
    public CampaignMediaResponse uploadMeetingAttachment(
            Long campaignId,
            Long meetingId,
            MultipartFile file,
            User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);
        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);
        return mediaService.uploadCampaignMeetingAttachment(file, campaign, meeting);
    }

    @Override
    @Transactional
    public CampaignMediaResponse deleteMeetingAttachment(
            Long campaignId,
            Long meetingId,
            Long attachmentId,
            User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);
        getMeetingInCampaign(campaignId, meetingId);

        CampaignMedia media = campaignMediaRepository
                .findByIdAndCampaignIdAndMeetingIdAndDeletedAtIsNull(attachmentId, campaignId, meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_MEDIA_NOT_FOUND,
                        "Campaign meeting attachment not found with ID: " + attachmentId));
        return mediaService.softDeleteCampaignMeetingAttachment(media);
    }

    @Override
    @Transactional
    public CampaignMeetingResponse cancelMeeting(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);

        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);
        validateMeetingIsUpcomingForCancel(meeting);
        User hostUser = meeting.getCreatedBy();

        meeting.setStatus(CampaignMeetingStatus.CANCELLED);
        meeting.setCalendarSequence(nextCalendarSequence(meeting));
        meeting.setCancelledAt(campaignMeetingClock.now());
        meeting.setCancelledBy(currentUser);
        meeting.setUpdatedAt(campaignMeetingClock.now());
        meeting.setUpdatedBy(currentUser);

        CampaignMeeting saved = campaignMeetingRepository.save(meeting);
        log.info("Campaign meeting cancelled: meetingId={}, campaignId={}, userId={}",
                meetingId, campaignId, currentUser.getId());
        eventPublisher.publishEvent(new CampaignMeetingWebexCancellationEvent(
                saved.getId(),
                saved.getWebexMeetingId(),
                hostUser != null ? hostUser.getId() : null));
        campaignMeetingInvitationService.sendCancellationNotice(saved);

        return toResponse(saved, currentUser);
    }

    private String generateCalendarUid() {
        return "campaign-meeting-" + UUID.randomUUID() + "@mgmgives";
    }

    private int nextCalendarSequence(CampaignMeeting meeting) {
        return (meeting.getCalendarSequence() == null ? 0 : meeting.getCalendarSequence()) + 1;
    }

    private Campaign getCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + campaignId));
    }

    private CampaignMeeting getMeetingInCampaign(Long campaignId, Long meetingId) {
        CampaignMeeting meeting = campaignMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_ERROR,
                        "Campaign meeting not found with ID: " + meetingId));

        if (meeting.getCampaign() == null || !campaignId.equals(meeting.getCampaign().getId())) {
            throw new ResourceNotFoundException(
                    ErrorCode.VALIDATION_ERROR,
                    "Campaign meeting not found with ID: " + meetingId);
        }
        return meeting;
    }

    private void validateCampaignCanHaveMeetings(Campaign campaign) {
        if (campaign.getStatus() != CampaignStatus.APPROVED && campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(
                    ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE,
                    "Meetings can only be created for APPROVED or IN_PROGRESS campaigns");
        }
    }

    private void validateMeetingTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Meeting start time and end time are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Meeting end time must be after start time");
        }
    }

    private void validateMeetingTypeDetails(CampaignMeetingType meetingType, String location, String meetingUrl) {
        if (requiresRoom(meetingType) && !StringUtils.hasText(location)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Location is required for offline or hybrid meetings");
        }
        if (requiresOnlineMeeting(meetingType) && StringUtils.hasText(meetingUrl)) {
            validateMeetingUrl(meetingUrl);
        }
    }

    private String normalizeLocation(String location) {
        return StringUtils.hasText(location) ? location.trim() : null;
    }

    private String normalizeMeetingUrl(String meetingUrl) {
        return StringUtils.hasText(meetingUrl) ? meetingUrl.trim() : null;
    }

    private String webexAgenda(String description) {
        String plainText = webexAgendaPlainText(description);
        if (plainText == null || plainText.length() <= WEBEX_AGENDA_MAX_LENGTH) {
            return plainText;
        }

        int maxContentLength = WEBEX_AGENDA_MAX_LENGTH - TRUNCATED_AGENDA_SUFFIX.length();
        return plainText.substring(0, maxContentLength) + TRUNCATED_AGENDA_SUFFIX;
    }

    private String webexAgendaPlainText(String description) {
        if (description == null) {
            return null;
        }

        String htmlWithLineBreaks = decodeHtmlEntities(description)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n");
        String plainText = Jsoup.parse(htmlWithLineBreaks)
                .wholeText()
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        return plainText.isEmpty() ? null : plainText;
    }

    private String decodeHtmlEntities(String value) {
        return Parser.unescapeEntities(value, false);
    }

    private boolean requiresRoom(CampaignMeetingType meetingType) {
        return meetingType == CampaignMeetingType.OFFLINE || meetingType == CampaignMeetingType.HYBRID;
    }

    private boolean requiresOnlineMeeting(CampaignMeetingType meetingType) {
        return meetingType == CampaignMeetingType.ONLINE || meetingType == CampaignMeetingType.HYBRID;
    }

    private void validateMeetingUrl(String meetingUrl) {
        try {
            URI uri = new URI(meetingUrl);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Meeting URL must be a valid HTTP or HTTPS URL");
            }
        } catch (URISyntaxException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Meeting URL must be a valid HTTP or HTTPS URL");
        }
    }

    private CampaignMeetingType meetingType(CampaignMeeting meeting) {
        return meeting.getMeetingType() != null ? meeting.getMeetingType() : CampaignMeetingType.ONLINE;
    }

    private void validateMeetingTimeConflict(
            Long campaignId,
            Long excludedMeetingId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        boolean hasConflict = campaignMeetingRepository.existsOverlappingMeeting(
                campaignId,
                startTime,
                endTime,
                excludedMeetingId,
                List.of(CampaignMeetingStatus.UPCOMING, CampaignMeetingStatus.IN_PROGRESS));
        if (hasConflict) {
            throw new AppException(
                    ErrorCode.MEETING_TIME_CONFLICT,
                    "This campaign already has a meeting scheduled during this time.");
        }
    }

    private void validateMeetingIsUpcomingForUpdate(CampaignMeeting meeting) {
        if (!isUpcoming(meeting, campaignMeetingClock.now())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Only upcoming scheduled meetings can be updated");
        }
    }

    private void validateMeetingIsUpcomingForCancel(CampaignMeeting meeting) {
        if (!isUpcoming(meeting, campaignMeetingClock.now())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Only upcoming scheduled meetings can be cancelled");
        }
    }

    private void validateRecipientsImmutable(UpdateCampaignMeetingRequest request) {
        if (request.notifyAllMembers() != null || request.recipientUserIds() != null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Meeting recipients can only be selected when creating a meeting");
        }
    }

    private List<User> resolveRecipients(Long campaignId, boolean notifyAll, List<Long> recipientUserIds) {
        if (notifyAll) {
            return resolveAllRecipients(campaignId)
                    .stream()
                    .map(CampaignMember::getUser)
                    .toList();
        }

        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "recipientUserIds is required when notifyAll is false");
        }

        Set<Long> selectedIds = new LinkedHashSet<>(recipientUserIds);
        Map<Long, CampaignMember> membersByUserId = new LinkedHashMap<>();
        for (CampaignMember member : campaignMemberRepository.findByCampaignId(campaignId)) {
            if (member.getUser() != null && member.getUser().getId() != null) {
                membersByUserId.put(member.getUser().getId(), member);
            }
        }

        List<Long> invalidIds = selectedIds.stream()
                .filter(userId -> !membersByUserId.containsKey(userId))
                .toList();
        if (!invalidIds.isEmpty()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Selected recipients must be campaign members: " + invalidIds);
        }

        List<User> recipients = new ArrayList<>();
        Set<String> seenEmails = new LinkedHashSet<>();
        for (Long selectedId : selectedIds) {
            User user = membersByUserId.get(selectedId).getUser();
            if (isReceivableRecipient(user) && seenEmails.add(user.getEmail().toLowerCase())) {
                recipients.add(user);
            }
        }
        if (recipients.size() != selectedIds.size()) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Selected recipients must be active campaign members with email addresses");
        }
        return recipients;
    }

    private List<CampaignMember> resolveAllRecipients(Long campaignId) {
        Set<String> seenEmails = new LinkedHashSet<>();
        return campaignMemberRepository.findByCampaignId(campaignId)
                .stream()
                .filter(member -> !member.getRoleInCampaign().equals(CampaignMemberRole.CAMPAIGN_ADMIN))
                .filter(member -> member.getUser() != null)
                .filter(member -> isReceivableRecipient(member.getUser()))
                .filter(member -> seenEmails.add(member.getUser().getEmail().toLowerCase()))
                .toList();
    }

    private List<CampaignMember> resolveInvitedMembers(CampaignMeeting meeting) {
        Long campaignId = meeting.getCampaign() != null ? meeting.getCampaign().getId() : null;
        if (campaignId == null) {
            return List.of();
        }
        Set<Long> invitedIds = parseUserIds(meeting.getInvitedUserIds());
        if (meeting.isNotifyAll() || invitedIds.isEmpty()) {
            return resolveAllRecipients(campaignId);
        }
        return resolveMembersByUserIds(campaignId, invitedIds);
    }

    private List<CampaignMember> resolveMembersByUserIds(Long campaignId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<String> seenEmails = new LinkedHashSet<>();
        return campaignMemberRepository.findByCampaignId(campaignId)
                .stream()
                .filter(member -> member.getUser() != null)
                .filter(member -> userIds.contains(member.getUser().getId()))
                .filter(member -> isReceivableRecipient(member.getUser()))
                .filter(member -> seenEmails.add(member.getUser().getEmail().toLowerCase()))
                .toList();
    }

    private List<User> users(List<CampaignMember> members) {
        return members.stream()
                .map(CampaignMember::getUser)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isReceivableRecipient(User user) {
        return user != null
                && user.getStatus() == UserStatus.ACTIVE
                && StringUtils.hasText(user.getEmail());
    }

    private String serializeUserIds(List<User> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        return users.stream()
                .filter(user -> user != null && user.getId() != null)
                .map(User::getId)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Set<Long> parseUserIds(String userIds) {
        if (!StringUtils.hasText(userIds)) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : userIds.split(",")) {
            String trimmed = value.trim();
            if (StringUtils.hasText(trimmed)) {
                ids.add(Long.parseLong(trimmed));
            }
        }
        return ids;
    }

    private boolean matchesView(CampaignMeeting meeting, String view, LocalDateTime now) {
        String normalizedView = view == null || view.isBlank() ? "default" : view.trim().toLowerCase();
        return switch (normalizedView) {
            case "upcoming", "default" -> meeting.getStatus() == CampaignMeetingStatus.UPCOMING
                    && meeting.getEndTime() != null
                    && meeting.getEndTime().isAfter(now);
            case "in-progress" -> meeting.getStatus() == CampaignMeetingStatus.IN_PROGRESS;
            case "past" -> isPast(meeting, now);
            case "all" -> true;
            default -> throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Unsupported meeting view: " + view);
        };
    }

    private Comparator<CampaignMeeting> meetingComparator(String view) {
        return Comparator.comparing(CampaignMeeting::getStartTime).reversed();
    }

    private boolean isUpcoming(CampaignMeeting meeting, LocalDateTime now) {
        return effectiveStatus(meeting, now) == CampaignMeetingStatus.UPCOMING;
    }

    private boolean isLive(CampaignMeeting meeting, LocalDateTime now) {
        return meeting.getStatus() == CampaignMeetingStatus.UPCOMING
                && meeting.getStartTime() != null
                && meeting.getEndTime() != null
                && !meeting.getStartTime().isAfter(now)
                && meeting.getEndTime().isAfter(now);
    }

    private boolean isPast(CampaignMeeting meeting, LocalDateTime now) {
        return meeting.getStatus() == CampaignMeetingStatus.CANCELLED
                || meeting.getStatus() == CampaignMeetingStatus.ENDED
                || (meeting.getEndTime() != null && !meeting.getEndTime().isAfter(now));
    }

    private CampaignMeetingStatus mapStatus(String webexState, CampaignMeetingStatus currentStatus) {
        log.info("Mapping status for webex state: {}, currentStatus={}", webexState, currentStatus);
        if ("inProgress".equals(webexState))
            return CampaignMeetingStatus.IN_PROGRESS;
        if ("ended".equals(webexState) || "expired".equals(webexState))
            return CampaignMeetingStatus.ENDED;
        if ("cancelled".equals(webexState))
            return CampaignMeetingStatus.CANCELLED;
        if ("active".equals(webexState) && currentStatus == CampaignMeetingStatus.IN_PROGRESS)
            return CampaignMeetingStatus.ENDED;

        return CampaignMeetingStatus.UPCOMING;
    }

    private CampaignMeetingStatus mapScheduledStatus(CampaignMeeting meeting, LocalDateTime now) {
        if (meeting.getStatus() == CampaignMeetingStatus.CANCELLED
                || meeting.getStatus() == CampaignMeetingStatus.ENDED
                || meeting.getStatus() == CampaignMeetingStatus.EXPIRED) {
            return meeting.getStatus();
        }
        if (meeting.getEndTime() != null && !meeting.getEndTime().isAfter(now)) {
            return CampaignMeetingStatus.ENDED;
        }
        if (meeting.getStartTime() != null && !meeting.getStartTime().isAfter(now)) {
            return CampaignMeetingStatus.IN_PROGRESS;
        }
        return CampaignMeetingStatus.UPCOMING;
    }

    private CampaignMeetingStatus effectiveStatus(CampaignMeeting meeting, LocalDateTime now) {
        if (meetingType(meeting) == CampaignMeetingType.OFFLINE
                && (meeting.getStatus() == CampaignMeetingStatus.UPCOMING
                || meeting.getStatus() == CampaignMeetingStatus.IN_PROGRESS)) {
            return mapScheduledStatus(meeting, now);
        }
        return meeting.getStatus();
    }

    private void syncScheduledMeetingStatuses(LocalDateTime now) {
        campaignMeetingRepository.markScheduledMeetingsEnded(now);
        campaignMeetingRepository.markScheduledMeetingsInProgress(now);
    }

    private boolean isEndedLiveSession(
            String webexState,
            CampaignMeetingStatus currentStatus,
            CampaignMeetingStatus newStatus) {
        return "active".equals(webexState)
                && currentStatus == CampaignMeetingStatus.IN_PROGRESS
                && newStatus == CampaignMeetingStatus.ENDED;
    }

    private void requireCampaignAdmin(Campaign campaign, User currentUser) {
        campaignAccessHelper.validateCampaignAdmin(campaign.getId(), currentUser, ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE);
    }

    private boolean canManageMeeting(Campaign campaign, User currentUser) {
        return campaignAccessHelper.isCampaignAdmin(campaign.getId(), currentUser);
    }

    private void requireMeetingViewer(Campaign campaign, User currentUser) {
        campaignAccessHelper.validateCampaignMemberOrAdmin(campaign.getId(), currentUser, ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS);
    }

    private CampaignMeetingResponse toResponse(CampaignMeeting meeting, User currentUser) {
        boolean canManage = canManageMeeting(meeting.getCampaign(), currentUser);
        LocalDateTime now = campaignMeetingClock.now();
        CampaignMeetingStatus effectiveStatus = effectiveStatus(meeting, now);
        boolean upcoming = effectiveStatus == CampaignMeetingStatus.UPCOMING;
        return CampaignMeetingResponse.builder()
                .id(meeting.getId())
                .campaignId(meeting.getCampaign() != null ? meeting.getCampaign().getId() : null)
                .createdById(meeting.getCreatedBy() != null ? meeting.getCreatedBy().getId() : null)
                .createdByName(meeting.getCreatedBy() != null ? meeting.getCreatedBy().getFullName() : null)
                .webexMeetingId(meeting.getWebexMeetingId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .meetingUrl(meeting.getMeetingUrl())
                .meetingType(meetingType(meeting))
                .location(meeting.getLocation())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .status(meeting.getStatus())
                .notifyAllMembers(meeting.isNotifyAll())
                .invitedCount(invitedCount(meeting))
                .invitedUserIds(canManage ? invitedUserIdsForResponse(meeting) : null)
                .displayStatus(effectiveStatus.name())
                .canManage(canManage)
                .canUpdate(canManage && upcoming)
                .canCancel(canManage && upcoming)
                .canEditNotes(canManage)
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .build();
    }

    private int invitedCount(CampaignMeeting meeting) {
        if (meeting.getInvitedCount() != null) {
            return meeting.getInvitedCount();
        }
        return resolveInvitedMembers(meeting).size();
    }

    private List<Long> invitedUserIdsForResponse(CampaignMeeting meeting) {
        return resolveInvitedMembers(meeting)
                .stream()
                .map(CampaignMember::getUser)
                .filter(user -> user != null && user.getId() != null)
                .map(User::getId)
                .toList();
    }

    private MeetingNotesResponse toNotesResponse(CampaignMeeting meeting, boolean canEdit) {
        User updatedBy = meeting.getNotesUpdatedBy();
        return new MeetingNotesResponse(
                meeting.getId(),
                meeting.getNotes(),
                meeting.getNotesUpdatedAt(),
                updatedBy != null ? updatedBy.getId() : null,
                actorName(updatedBy, null),
                canEdit);
    }

    private CampaignMediaResponse toMediaResponse(CampaignMedia media) {
        return new CampaignMediaResponse(media.getId(), media.getUrl(), media.getMediaType(), media.isCover(),
                media.getCaption(), media.getDisplayOrder(), media.getContext().name());
    }

    private String actorName(User user, String fallback) {
        if (user == null) {
            return fallback;
        }
        if (StringUtils.hasText(user.getFullName())) {
            return user.getFullName();
        }
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail();
        }
        return fallback;
    }
}
