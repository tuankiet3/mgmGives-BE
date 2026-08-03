package com.mgmtp.gives.notification.impl;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.notification.NotificationRecipientResolver;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class NotificationRecipientResolverImpl implements NotificationRecipientResolver {

    private final CampaignFollowerRepository campaignFollowerRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<NotificationRecipient> campaignFollowers(Long campaignId) {
        return new HashSet<>(
                campaignFollowerRepository.findFollowerRecipientsByCampaignId(campaignId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Set<NotificationRecipient> campaignOwner(Long campaignId) {
        return campaignRepository.findOwnerRecipientByCampaignId(campaignId)
                .map(recipient -> {
                    log.info(
                            "Campaign owner recipient resolved: campaignId={}, ownerUserId={}, ownerEmail={}",
                            campaignId,
                            recipient.userId(),
                            recipient.email()
                    );

                    return Set.of(recipient);
                })
                .orElseGet(() -> {
                    log.warn("Campaign owner recipient not found: campaignId={}", campaignId);
                    return Set.of();
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Set<NotificationRecipient> singleUser(Long userId) {
        if (userId == null) {
            return Set.of();
        }

        return userRepository.findNotificationRecipientById(userId)
                .map(Set::of)
                .orElseGet(() -> {
                    log.warn("Notification recipient user not found: userId={}", userId);
                    return Set.of();
                });
    }

    @Override
    public Set<NotificationRecipient> campaignOwnerAndFollowersExceptDonor(Long campaignId, Long donorUserId) {

        Set<NotificationRecipient> recipients = new HashSet<>(campaignOwner(campaignId));

        List<User> followers = campaignFollowerRepository.findFollowerUsersByCampaignId(campaignId);

        followers.stream()
                .filter(user -> donorUserId == null || !user.getId().equals(donorUserId))
                .map(user -> new NotificationRecipient(user.getId(), user.getEmail()))
                .forEach(recipients::add);

        return recipients;
    }

    @Override
    public Set<NotificationRecipient> campaignOwnerAndFollowers(Long campaignId) {
        Map<Long, NotificationRecipient> recipients = new LinkedHashMap<>();

        for (NotificationRecipient owner : campaignOwner(campaignId)) {
            recipients.put(owner.userId(), owner);
        }

        List<User> followers = campaignFollowerRepository.findFollowerUsersByCampaignId(campaignId);

        for (User follower : followers) {
            recipients.put(
                    follower.getId(),
                    new NotificationRecipient(follower.getId(), follower.getEmail())
            );
        }

        return new LinkedHashSet<>(recipients.values());
    }

    @Override
    public Set<NotificationRecipient> campaignAdmins(Long campaignId) {
        Map<Long, NotificationRecipient> recipients = new LinkedHashMap<>();

        for (NotificationRecipient owner : campaignOwner(campaignId)) {
            recipients.put(owner.userId(), owner);
        }

        campaignMemberRepository.findRecipientsByCampaignIdAndRole(campaignId, CampaignMemberRole.CAMPAIGN_ADMIN)
                .forEach(recipient -> recipients.put(recipient.userId(), recipient));

        return new LinkedHashSet<>(recipients.values());
    }
}
