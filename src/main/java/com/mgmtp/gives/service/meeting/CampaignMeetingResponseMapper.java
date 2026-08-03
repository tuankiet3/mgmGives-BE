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
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.service.CampaignMeetingClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CampaignMeetingResponseMapper {
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMeetingRecipientResolver recipientResolver;
    private final CampaignMeetingClock campaignMeetingClock;

    public CampaignMeetingResponse toResponse(CampaignMeeting meeting, User currentUser) {
        boolean canManage = canManageMeeting(meeting.getCampaign(), currentUser);
        boolean upcoming = isUpcoming(meeting, campaignMeetingClock.now());
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
                .invitedCount(recipientResolver.invitedCount(meeting))
                .invitedUserIds(canManage ? recipientResolver.invitedUserIds(meeting) : null)
                .displayStatus(meeting.getStatus().name())
                .canManage(canManage)
                .canUpdate(canManage && upcoming)
                .canCancel(canManage && upcoming)
                .canEditNotes(canManage)
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .build();
    }

    public MeetingNotesResponse toNotesResponse(CampaignMeeting meeting, boolean canEdit) {
        User updatedBy = meeting.getNotesUpdatedBy();
        return new MeetingNotesResponse(
                meeting.getId(),
                meeting.getNotes(),
                meeting.getNotesUpdatedAt(),
                updatedBy != null ? updatedBy.getId() : null,
                actorName(updatedBy, null),
                canEdit);
    }

    public List<MeetingActivityResponse> toActivityResponses(CampaignMeeting meeting) {
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
                    "Invitations were sent to " + recipientResolver.invitedCount(meeting) + " members",
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

    public CampaignMediaResponse toMediaResponse(CampaignMedia media) {
        return new CampaignMediaResponse(
                media.getId(),
                media.getUrl(),
                media.getMediaType(),
                media.isCover(),
                media.getCaption(),
                media.getDisplayOrder(),
                media.getContext().name());
    }

    private boolean canManageMeeting(Campaign campaign, User currentUser) {
        if (campaign == null || currentUser == null || currentUser.getId() == null) {
            return false;
        }
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());
        boolean isCampaignAdmin = campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaign.getId(),
                currentUser.getId(),
                CampaignMemberRole.CAMPAIGN_ADMIN);
        return isCreator || isCampaignAdmin;
    }

    private boolean isUpcoming(CampaignMeeting meeting, LocalDateTime now) {
        return meeting.getStatus() == CampaignMeetingStatus.UPCOMING
                && meeting.getStartTime() != null
                && meeting.getStartTime().isAfter(now);
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
