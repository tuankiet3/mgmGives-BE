package com.mgmtp.gives.dto.campaign;

import java.util.List;

public record DonorThankYouContext(
        Long userId,
        String fullName,
        long totalMoneyAmount,
        int donationCount,
        List<String> goodsItems
) {
}
