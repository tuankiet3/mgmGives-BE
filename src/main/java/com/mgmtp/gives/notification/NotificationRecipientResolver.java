package com.mgmtp.gives.notification;

import com.mgmtp.gives.dto.notification.NotificationRecipient;

import java.util.Set;

public interface NotificationRecipientResolver {
    Set<NotificationRecipient> campaignFollowers(Long campaignId);

    Set<NotificationRecipient> campaignOwner(Long campaignId);

    Set<NotificationRecipient> singleUser(Long userId);

    Set<NotificationRecipient> campaignOwnerAndFollowersExceptDonor(Long campaignId, Long donorUserId);

    Set<NotificationRecipient> campaignOwnerAndFollowers(Long campaignId);

    Set<NotificationRecipient> campaignAdmins(Long campaignId);
}
