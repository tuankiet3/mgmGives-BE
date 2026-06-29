package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.notification.CreateNotificationCommand;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.dto.donation.DonationNotification;

public interface NotificationService {
    void createNotification(CreateNotificationCommand command);
}
