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
import com.mgmtp.gives.dto.webex.WebexMeetingResult;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignMeetingRepository;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.service.CampaignMeetingInvitationService;
import com.mgmtp.gives.service.CampaignMeetingService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.service.UserWebexConnectionService;
import com.mgmtp.gives.service.WebexMeetingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignMeetingServiceImpl implements CampaignMeetingService {
    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMeetingRepository campaignMeetingRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final WebexMeetingClient webexMeetingClient;
    private final CampaignMeetingInvitationService campaignMeetingInvitationService;
    private final MediaService mediaService;
    private final UserWebexConnectionService userWebexConnectionService;

    @Override
    @Transactional
    public CampaignMeetingResponse createMeeting(Long campaignId, CreateCampaignMeetingRequest request, User currentUser) {
        Campaign campaign = getCampaign(campaignId);

        validateMeetingTime(request.startTime(), request.endTime());
        validateCampaignCanHaveMeetings(campaign);
        requireCampaignAdmin(campaign, currentUser);
        validateMeetingTimeConflict(campaign.getId(), null, request.startTime(), request.endTime());
        boolean notifyAll = request.notifyAllMembers() == null || request.notifyAllMembers();
        List<User> recipients = resolveRecipients(campaign.getId(), notifyAll, request.recipientUserIds());
        String accessToken = userWebexConnectionService.getValidAccessToken(currentUser);

        WebexMeetingResult webexMeeting = webexMeetingClient.createMeeting(new WebexCreateMeetingCommand(
                request.title(),
                request.description(),
                request.startTime(),
                request.endTime()
        ), accessToken);

        CampaignMeeting meeting = CampaignMeeting.builder()
                .campaign(campaign)
                .createdBy(currentUser)
                .webexMeetingId(webexMeeting.id())
                .title(request.title())
                .description(request.description())
                .meetingUrl(webexMeeting.webLink())
                .notifyAll(notifyAll)
                .invitedUserIds(serializeUserIds(recipients))
                .invitedCount(recipients.size())
                .invitationsSentAt(LocalDateTime.now())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(CampaignMeetingStatus.SCHEDULED)
                .updatedAt(LocalDateTime.now())
                .build();

        CampaignMeeting saved = campaignMeetingRepository.save(meeting);
        log.info("Campaign meeting created: meetingId={}, campaignId={}, userId={}",
                saved.getId(), campaignId, currentUser.getId());
        campaignMeetingInvitationService.sendInvitations(saved, recipients);

        return toResponse(saved, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMeetingResponse> getMeetings(Long campaignId, String view, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);

        LocalDateTime now = LocalDateTime.now();
        return campaignMeetingRepository.findByCampaignIdOrderByStartTimeAsc(campaignId)
                .stream()
                .filter(meeting -> matchesView(meeting, view, now))
                .sorted(meetingComparator(view))
                .map(meeting -> toResponse(meeting, currentUser))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMeetingResponse getMeeting(Long campaignId, Long meetingId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireMeetingViewer(campaign, currentUser);
        return toResponse(getMeetingInCampaign(campaignId, meetingId), currentUser);
    }

    @Override
    @Transactional
    public CampaignMeetingResponse updateMeeting(
            Long campaignId,
            Long meetingId,
            UpdateCampaignMeetingRequest request,
            User currentUser
    ) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);

        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);
        validateMeetingIsUpcomingForUpdate(meeting);
        validateRecipientsImmutable(request);

        String title = request.title() != null ? request.title().trim() : meeting.getTitle();
        String description = request.description() != null ? request.description() : meeting.getDescription();
        LocalDateTime startTime = request.startTime() != null ? request.startTime() : meeting.getStartTime();
        LocalDateTime endTime = request.endTime() != null ? request.endTime() : meeting.getEndTime();

        if (!StringUtils.hasText(title)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Title must not be blank");
        }
        validateMeetingTime(startTime, endTime);
        validateMeetingTimeConflict(campaignId, meetingId, startTime, endTime);
        User hostUser = meeting.getCreatedBy();
        String accessToken = userWebexConnectionService.getValidAccessToken(hostUser);

        WebexMeetingResult webexMeeting = webexMeetingClient.updateMeeting(
                meeting.getWebexMeetingId(),
                new WebexCreateMeetingCommand(title, description, startTime, endTime),
                accessToken
        );

        meeting.setTitle(title);
        meeting.setDescription(description);
        meeting.setStartTime(startTime);
        meeting.setEndTime(endTime);
        meeting.setMeetingUrl(webexMeeting.webLink());
        meeting.setUpdatedAt(LocalDateTime.now());
        meeting.setUpdatedBy(currentUser);

        CampaignMeeting saved = campaignMeetingRepository.save(meeting);
        log.info("Campaign meeting updated: meetingId={}, campaignId={}, userId={}",
                meetingId, campaignId, currentUser.getId());
        return toResponse(saved, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMeetingRecipientResponse> getMeetingRecipients(Long campaignId, User currentUser) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);

        return resolveAllRecipients(campaignId)
                .stream()
                .map(member -> new CampaignMeetingRecipientResponse(
                        member.getUser().getId(),
                        member.getUser().getFullName(),
                        member.getUser().getEmail(),
                        member.getRoleInCampaign()
                ))
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
                        member.getRoleInCampaign()
                ))
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
            User currentUser
    ) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);
        CampaignMeeting meeting = getMeetingInCampaign(campaignId, meetingId);

        LocalDateTime now = LocalDateTime.now();
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
                meeting.getCreatedAt()
        ));
        if (meeting.getInvitationsSentAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "INVITATIONS_SENT",
                    "Invitations were sent to " + invitedCount(meeting) + " members",
                    null,
                    "System",
                    meeting.getInvitationsSentAt()
            ));
        }
        if (meeting.getUpdatedBy() != null && meeting.getUpdatedAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "UPDATED",
                    "Meeting was updated",
                    meeting.getUpdatedBy().getId(),
                    actorName(meeting.getUpdatedBy(), "Unknown"),
                    meeting.getUpdatedAt()
            ));
        }
        if (meeting.getCancelledAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "CANCELLED",
                    "Meeting was cancelled",
                    meeting.getCancelledBy() != null ? meeting.getCancelledBy().getId() : null,
                    actorName(meeting.getCancelledBy(), "Unknown"),
                    meeting.getCancelledAt()
            ));
        }
        if (meeting.getNotesUpdatedAt() != null) {
            activities.add(new MeetingActivityResponse(
                    "NOTES_UPDATED",
                    "Meeting notes were updated",
                    meeting.getNotesUpdatedBy() != null ? meeting.getNotesUpdatedBy().getId() : null,
                    actorName(meeting.getNotesUpdatedBy(), "Unknown"),
                    meeting.getNotesUpdatedAt()
            ));
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
            User currentUser
    ) {
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
            User currentUser
    ) {
        Campaign campaign = getCampaign(campaignId);
        requireCampaignAdmin(campaign, currentUser);
        getMeetingInCampaign(campaignId, meetingId);

        CampaignMedia media = campaignMediaRepository
                .findByIdAndCampaignIdAndMeetingIdAndDeletedAtIsNull(attachmentId, campaignId, meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_MEDIA_NOT_FOUND,
                        "Campaign meeting attachment not found with ID: " + attachmentId
                ));
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
        String accessToken = userWebexConnectionService.getValidAccessToken(hostUser);

        webexMeetingClient.cancelMeeting(meeting.getWebexMeetingId(), accessToken);
        meeting.setStatus(CampaignMeetingStatus.CANCELLED);
        meeting.setCancelledAt(LocalDateTime.now());
        meeting.setCancelledBy(currentUser);
        meeting.setUpdatedAt(LocalDateTime.now());
        meeting.setUpdatedBy(currentUser);

        CampaignMeeting saved = campaignMeetingRepository.save(meeting);
        log.info("Campaign meeting cancelled: meetingId={}, campaignId={}, userId={}",
                meetingId, campaignId, currentUser.getId());
        campaignMeetingInvitationService.sendCancellationNotice(saved);

        return toResponse(saved, currentUser);
    }

    private Campaign getCampaign(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + campaignId
                ));
    }

    private CampaignMeeting getMeetingInCampaign(Long campaignId, Long meetingId) {
        CampaignMeeting meeting = campaignMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.VALIDATION_ERROR,
                        "Campaign meeting not found with ID: " + meetingId
                ));

        if (meeting.getCampaign() == null || !campaignId.equals(meeting.getCampaign().getId())) {
            throw new ResourceNotFoundException(
                    ErrorCode.VALIDATION_ERROR,
                    "Campaign meeting not found with ID: " + meetingId
            );
        }
        return meeting;
    }

    private void validateCampaignCanHaveMeetings(Campaign campaign) {
        if (campaign.getStatus() != CampaignStatus.APPROVED && campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(
                    ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE,
                    "Meetings can only be created for APPROVED or IN_PROGRESS campaigns"
            );
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

    private void validateMeetingTimeConflict(
            Long campaignId,
            Long excludedMeetingId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        boolean hasConflict = campaignMeetingRepository.existsOverlappingActiveMeeting(
                campaignId,
                startTime,
                endTime,
                excludedMeetingId,
                CampaignMeetingStatus.CANCELLED
        );
        if (hasConflict) {
            throw new AppException(
                    ErrorCode.MEETING_TIME_CONFLICT,
                    "This campaign already has a meeting scheduled during this time."
            );
        }
    }

    private void validateMeetingIsUpcomingForUpdate(CampaignMeeting meeting) {
        if (!isUpcoming(meeting, LocalDateTime.now())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Only upcoming scheduled meetings can be updated"
            );
        }
    }

    private void validateMeetingIsUpcomingForCancel(CampaignMeeting meeting) {
        if (!isUpcoming(meeting, LocalDateTime.now())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Only upcoming scheduled meetings can be cancelled"
            );
        }
    }

    private void validateRecipientsImmutable(UpdateCampaignMeetingRequest request) {
        if (request.notifyAllMembers() != null || request.recipientUserIds() != null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Meeting recipients can only be selected when creating a meeting"
            );
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
                    "recipientUserIds is required when notifyAll is false"
            );
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
                    "Selected recipients must be campaign members: " + invalidIds
            );
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
                    "Selected recipients must be active campaign members with email addresses"
            );
        }
        return recipients;
    }

    private List<CampaignMember> resolveAllRecipients(Long campaignId) {
        Set<String> seenEmails = new LinkedHashSet<>();
        return campaignMemberRepository.findByCampaignId(campaignId)
                .stream()
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
            case "upcoming", "default" -> meeting.getStatus() == CampaignMeetingStatus.SCHEDULED
                    && meeting.getEndTime() != null
                    && meeting.getEndTime().isAfter(now);
            case "past" -> isPast(meeting, now);
            case "all" -> true;
            default -> throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Unsupported meeting view: " + view
            );
        };
    }

    private Comparator<CampaignMeeting> meetingComparator(String view) {
        String normalizedView = view == null || view.isBlank() ? "default" : view.trim().toLowerCase();
        Comparator<CampaignMeeting> ascending = Comparator.comparing(CampaignMeeting::getStartTime);
        if ("past".equals(normalizedView)) {
            return ascending.reversed();
        }
        return ascending;
    }

    private boolean isUpcoming(CampaignMeeting meeting, LocalDateTime now) {
        return meeting.getStatus() == CampaignMeetingStatus.SCHEDULED
                && meeting.getStartTime() != null
                && meeting.getStartTime().isAfter(now);
    }

    private boolean isLive(CampaignMeeting meeting, LocalDateTime now) {
        return meeting.getStatus() == CampaignMeetingStatus.SCHEDULED
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

    private String effectiveStatus(CampaignMeeting meeting) {
        LocalDateTime now = LocalDateTime.now();
        if (meeting.getStatus() == CampaignMeetingStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (isLive(meeting, now)) {
            return "LIVE";
        }
        if (isPast(meeting, now)) {
            return "ENDED";
        }
        return "UPCOMING";
    }

    private void requireCampaignAdmin(Campaign campaign, User currentUser) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User must be authenticated");
        }

        if (canManageMeeting(campaign, currentUser)) {
            return;
        }

        throw new AppException(
                ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                "Only Campaign Admin or global ADMIN can manage campaign meetings"
        );
    }

    private boolean canManageMeeting(Campaign campaign, User currentUser) {
        if (campaign == null || currentUser == null || currentUser.getId() == null) {
            return false;
        }
        boolean isGlobalAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());
        boolean isCampaignAdmin = campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaign.getId(),
                currentUser.getId(),
                CampaignMemberRole.CAMPAIGN_ADMIN
        );
        return isGlobalAdmin || isCreator || isCampaignAdmin;
    }

    private void requireMeetingViewer(Campaign campaign, User currentUser) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User must be authenticated");
        }

        boolean isGlobalAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());
        boolean isMember = campaignMemberRepository.existsByCampaignIdAndUserId(
                campaign.getId(),
                currentUser.getId()
        );

        if (!isGlobalAdmin && !isCreator && !isMember) {
            throw new AppException(
                    ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS,
                    "Only campaign members can view campaign meetings"
            );
        }
    }

    private CampaignMeetingResponse toResponse(CampaignMeeting meeting, User currentUser) {
        boolean canManage = canManageMeeting(meeting.getCampaign(), currentUser);
        boolean upcoming = isUpcoming(meeting, LocalDateTime.now());
        String displayStatus = effectiveStatus(meeting);
        return CampaignMeetingResponse.builder()
                .id(meeting.getId())
                .campaignId(meeting.getCampaign() != null ? meeting.getCampaign().getId() : null)
                .createdById(meeting.getCreatedBy() != null ? meeting.getCreatedBy().getId() : null)
                .createdByName(meeting.getCreatedBy() != null ? meeting.getCreatedBy().getFullName() : null)
                .webexMeetingId(meeting.getWebexMeetingId())
                .title(meeting.getTitle())
                .description(meeting.getDescription())
                .meetingUrl(meeting.getMeetingUrl())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .status(meeting.getStatus())
                .notifyAllMembers(meeting.isNotifyAll())
                .invitedCount(invitedCount(meeting))
                .invitedUserIds(canManage ? invitedUserIdsForResponse(meeting) : null)
                .displayStatus(displayStatus)
                .effectiveStatus(displayStatus)
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
                canEdit
        );
    }

    private CampaignMediaResponse toMediaResponse(CampaignMedia media) {
        return new CampaignMediaResponse(media.getId(), media.getUrl(), media.getMediaType(), media.isCover(),
                    media.getCaption(), media.getDisplayOrder(), media.getContext());
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
