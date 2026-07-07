package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.enums.DonationType;

public record CampaignDonationConfirmedEvent(
        Long donationId,
        Long campaignId,
        String campaignTitle,
        Long donorUserId,
        String donorName,
        boolean anonymous,
        DonationType donationType,
        Long amount,
        String goodsDescription,
        Long confirmedById
) {
}
