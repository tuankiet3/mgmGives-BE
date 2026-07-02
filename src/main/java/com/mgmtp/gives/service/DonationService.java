package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.donation.*;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DonationService {
    DonationResponse createDonation(DonationRequest request, User user);

    List<DonationResponse> getMyDonations(Long userId);

    List<DonationResponse> getPublicDonationsByCampaignId(Long campaignId);

    Page<DonationAdminResponse> getAllDonations(DonationStatus status, DonationType type, Long campaignId,
            Pageable pageable);

    DonationAdminResponse confirmDonation(Long donationId, User admin);

    PayOSResponse createPayOSDonation(PayOSRequest request, User user);

    DonationResponse confirmPayOSDonation(Long donationId);

    DonationResponse cancelPayOSDonation(Long donationId);

    DonationResponse confirmPayOSDonationByPaymentLinkId(String paymentLinkId);

    DonationResponse verifyPayOSUserTransaction(Long donationId);

    DonationResponse hideDonationMessage(Long donationId, boolean hidden, User currentUser);
}
