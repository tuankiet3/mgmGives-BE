package com.mgmtp.gives.service;

import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.dto.donation.DonationNotification;

public interface NotificationService {
    void notifyDonationStatus(User user, DonationNotification notification);
    void broadcastDonationUpdate(com.mgmtp.gives.entity.Donation donation);
}
