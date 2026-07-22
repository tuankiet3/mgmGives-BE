package com.mgmtp.gives.event.notification;

import com.mgmtp.gives.enums.DonationType;

public record DonationConfirmedEvent(
        Long donationId,
        Long campaignId,
        String campaignTitle,
        Long donorUserId,
        DonationType donationType,
        Long amount,
        String goodsDescription
) {
}
