package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.dto.campaign_meeting.CalendarAttendee;
import com.mgmtp.gives.dto.campaign_meeting.CalendarMeetingEmailRequest;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingCancellationEmailEvent;
import com.mgmtp.gives.event.campaign_meeting.CampaignMeetingInvitationEmailEvent;
import com.mgmtp.gives.service.CampaignMeetingInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignMeetingInvitationServiceImpl implements CampaignMeetingInvitationService {
    private final CampaignMemberRepository campaignMemberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public void sendInvitations(CampaignMeeting meeting) {
        Campaign campaign = meeting.getCampaign();
        if (campaign == null || campaign.getId() == null) {
            log.warn("Meeting invitation skipped because campaign is missing. meetingId={}", meeting.getId());
            return;
        }
        List<User> recipients = resolveRecipients(campaign.getId());
        sendInvitations(meeting, recipients);
    }

    @Override
    @Transactional
    public void sendInvitations(CampaignMeeting meeting, List<User> recipients) {
        Campaign campaign = meeting.getCampaign();
        if (campaign == null || campaign.getId() == null) {
            log.warn("Meeting invitation skipped because campaign is missing. meetingId={}", meeting.getId());
            return;
        }

        List<CalendarAttendee> attendees = attendees(recipients);
        for (User recipient : recipients) {
            eventPublisher.publishEvent(new CampaignMeetingInvitationEmailEvent(calendarRequest(
                    "REQUEST", meeting, campaign, recipient, attendees)));
        }
        log.info("Campaign meeting invitation events published: meetingId={}, campaignId={}, recipients={}",
                meeting.getId(), campaign.getId(), recipients.size());
    }

    @Override
    @Transactional(readOnly = true)
    public void sendCancellationNotice(CampaignMeeting meeting) {
        Campaign campaign = meeting.getCampaign();
        if (campaign == null || campaign.getId() == null) {
            log.warn("Meeting cancellation notice skipped because campaign is missing. meetingId={}", meeting.getId());
            return;
        }
        List<User> recipients = resolveRecipients(meeting)
                .stream()
                .filter(user -> user != null && user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> StringUtils.hasText(user.getEmail()))
                .toList();
        List<CalendarAttendee> attendees = attendees(recipients);
        for (User recipient : recipients) {
            eventPublisher.publishEvent(new CampaignMeetingCancellationEmailEvent(calendarRequest(
                    "CANCEL", meeting, campaign, recipient, attendees)));
        }
        log.info("Campaign meeting cancellation events published: meetingId={}, campaignId={}, recipients={}",
                meeting.getId(), campaign.getId(), recipients.size());
    }

    private List<User> resolveRecipients(CampaignMeeting meeting) {
        Campaign campaign = meeting.getCampaign();
        if (campaign == null || campaign.getId() == null || meeting.isNotifyAll()) {
            return resolveRecipients(campaign != null ? campaign.getId() : null);
        }

        Set<Long> invitedIds = parseUserIds(meeting.getInvitedUserIds());
        return campaignMemberRepository.findByCampaignId(campaign.getId())
                .stream()
                .map(CampaignMember::getUser)
                .filter(user -> user != null && invitedIds.contains(user.getId()))
                .toList();
    }

    private List<User> resolveRecipients(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        Set<String> seenEmails = new HashSet<>();
        return campaignMemberRepository.findByCampaignId(campaignId)
                .stream()
                .filter(member -> !member.getRoleInCampaign().equals(CampaignMemberRole.CAMPAIGN_ADMIN))
                .map(CampaignMember::getUser)
                .filter(user -> user != null && user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> StringUtils.hasText(user.getEmail()))
                .filter(user -> seenEmails.add(user.getEmail().toLowerCase()))
                .toList();
    }

    private Set<Long> parseUserIds(String userIds) {
        if (!StringUtils.hasText(userIds)) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(value -> ids.add(Long.parseLong(value)));
        return ids;
    }

    private String getCreatedByName(CampaignMeeting meeting) {
        User createdBy = meeting.getCreatedBy();
        if (createdBy == null) {
            return "Campaign Admin";
        }
        if (StringUtils.hasText(createdBy.getFullName())) {
            return createdBy.getFullName();
        }
        if (StringUtils.hasText(createdBy.getEmail())) {
            return createdBy.getEmail();
        }
        return "Campaign Admin";
    }

    private String getCreatedByEmail(CampaignMeeting meeting) {
        User createdBy = meeting.getCreatedBy();
        return createdBy != null && StringUtils.hasText(createdBy.getEmail()) ? createdBy.getEmail() : null;
    }

    private List<CalendarAttendee> attendees(List<User> recipients) {
        return recipients.stream()
                .filter(user -> user != null && StringUtils.hasText(user.getEmail()))
                .map(user -> new CalendarAttendee(user.getEmail(), user.getFullName()))
                .toList();
    }

    private CalendarMeetingEmailRequest calendarRequest(
            String method,
            CampaignMeeting meeting,
            Campaign campaign,
            User recipient,
            List<CalendarAttendee> attendees
    ) {
        return new CalendarMeetingEmailRequest(
                method,
                recipient.getEmail(),
                recipient.getFullName(),
                campaign.getTitle(),
                meeting.getTitle(),
                meeting.getDescription(),
                getCreatedByName(meeting),
                getCreatedByEmail(meeting),
                meeting.getMeetingUrl(),
                meeting.getLocation(),
                campaign.getId(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                meeting.getCalendarUid(),
                meeting.getCalendarSequence() == null ? 0 : meeting.getCalendarSequence(),
                attendees
        );
    }
}
