package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.donation.*;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DonationService {
    DonationResponse createDonation(DonationRequest request, User user, String idempotencyKey);

    Page<DonationResponse> getMyDonations(Long userId, DonationStatus status, DonationType type, Boolean anonymous, String search, Pageable pageable);

    List<DonationResponse> getPublicDonationsByCampaignId(Long campaignId);

    Page<DonationAdminResponse> getAllDonations(DonationStatus status, DonationType type, Long campaignId,
            String search, Pageable pageable);

    DonationAdminResponse confirmDonation(Long donationId, User admin);

    PayOSResponse createPayOSDonation(PayOSRequest request, User user);

    DonationResponse cancelPayOSDonation(Long donationId);

    DonationResponse confirmPayOSDonationByPaymentLinkId(String paymentLinkId);

    DonationResponse verifyPayOSUserTransaction(Long donationId);

    DonationResponse hideDonationMessage(Long donationId, boolean hidden, User currentUser);

    DonationResponse confirmCampaignDonation(Long donationId, User currentUser);

    DonationResponse rejectCampaignDonation(Long donationId, String reason, User currentUser);

    DonationResponse editCampaignDonation(Long donationId, EditDonationRequest request, User currentUser);

    List<DonationResponse> getCampaignDonationsForAdmin(Long campaignId, User currentUser);

    DonationResponse toggleDonationAmountVisibility(Long donationId, boolean hidden, User currentUser);

    DonationResponse submitManualProof(Long donationId, String proofUrl, User user);
}
