package com.mgmtp.gives.service.meeting;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingRecipientResponse;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CampaignMeetingRecipientResolver {
    private final CampaignMemberRepository campaignMemberRepository;

    public List<User> resolveForCreation(Long campaignId, boolean notifyAll, List<Long> recipientUserIds) {
        if (notifyAll) {
            return resolveAllMembers(campaignId)
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

    public List<CampaignMeetingRecipientResponse> listSelectableRecipients(Long campaignId) {
        return resolveAllMembers(campaignId)
                .stream()
                .map(this::toRecipientResponse)
                .toList();
    }

    public List<CampaignMeetingRecipientResponse> listInvitedRecipients(CampaignMeeting meeting) {
        return resolveInvitedMembers(meeting)
                .stream()
                .map(this::toRecipientResponse)
                .toList();
    }

    public String serializeUserIds(List<User> users) {
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

    public int invitedCount(CampaignMeeting meeting) {
        if (meeting.getInvitedCount() != null) {
            return meeting.getInvitedCount();
        }
        return resolveInvitedMembers(meeting).size();
    }

    public List<Long> invitedUserIds(CampaignMeeting meeting) {
        return resolveInvitedMembers(meeting)
                .stream()
                .map(CampaignMember::getUser)
                .filter(user -> user != null && user.getId() != null)
                .map(User::getId)
                .toList();
    }

    private List<CampaignMember> resolveAllMembers(Long campaignId) {
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
            return resolveAllMembers(campaignId);
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

    private CampaignMeetingRecipientResponse toRecipientResponse(CampaignMember member) {
        return new CampaignMeetingRecipientResponse(
                member.getUser().getId(),
                member.getUser().getFullName(),
                member.getUser().getEmail(),
                member.getRoleInCampaign());
    }
}
