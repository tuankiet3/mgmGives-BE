package com.mgmtp.gives.notification.publisher;

import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.event.notification.CampaignDonationConfirmedEvent;
import com.mgmtp.gives.event.notification.DonationConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class DonationNotificationPublisher {
    private final ApplicationEventPublisher eventPublisher;

    public void publishDonationConfirmedEvents(Donation donation) {
        publishDonorDonationConfirmedEvent(donation);
        publishCampaignDonationConfirmedEvent(donation);
    }

    public void publishDonorDonationConfirmedEvent(Donation donation) {
        eventPublisher.publishEvent(
                new DonationConfirmedEvent(
                        donation.getId(),
                        donation.getCampaign().getId(),
                        donation.getCampaign().getTitle(),
                        donation.getUser().getId(),
                        donation.getType(),
                        donation.getAmount(),
                        donation.getDetail()
                )
        );
    }

    public void publishCampaignDonationConfirmedEvent(Donation donation) {
        eventPublisher.publishEvent(
                new CampaignDonationConfirmedEvent(
                        donation.getId(),
                        donation.getCampaign().getId(),
                        donation.getCampaign().getTitle(),
                        donation.getUser().getId(),
                        getDonorName(donation),
                        donation.isAnonymous(),
                        donation.getType(),
                        donation.getAmount(),
                        donation.getDetail()
                )
        );
    }

    private String getDonorName(Donation donation) {
        if (donation.getUser() == null) {
            return "Unknown donor";
        }

        if (donation.getUser().getFullName() != null && !donation.getUser().getFullName().isBlank()) {
            return donation.getUser().getFullName();
        }

        return donation.getUser().getEmail();
    }
}
