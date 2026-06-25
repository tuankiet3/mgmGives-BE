package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.donation.DonationNotification;
import com.mgmtp.gives.entity.Notification;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.NotificationType;
import com.mgmtp.gives.repository.NotificationRepository;
import com.mgmtp.gives.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void notifyDonationStatus(User user, DonationNotification notification) {
        // 1. Persist notification in database
        Notification dbNotification = Notification.builder()
                .user(user)
                .title("Donation Status Update")
                .message(notification.message())
                .type(String.valueOf(NotificationType.DONATION))
                .isRead(false)
                .linkUrl("/donations/me")
                .build();
        notificationRepository.save(dbNotification);

        // 2. Broadcast privately via STOMP user destinations to /user/queue/donations
        messagingTemplate.convertAndSendToUser(
                user.getEmail(),
                "/queue/donations",
                notification
        );
    }

    @Override
    @Transactional
    public void broadcastDonationUpdate(com.mgmtp.gives.entity.Donation donation) {
        java.util.Map<String, Object> payload = java.util.Map.of(
                "donationId", donation.getId(),
                "campaignId", donation.getCampaign().getId(),
                "status", donation.getStatus().name(),
                "type", donation.getType().name()
        );
        messagingTemplate.convertAndSend(
                "/topic/campaigns/" + donation.getCampaign().getId() + "/donations",
                payload
        );
    }
}
