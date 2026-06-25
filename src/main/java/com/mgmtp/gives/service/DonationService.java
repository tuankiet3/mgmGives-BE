package com.mgmtp.gives.service;

import com.mgmtp.gives.dto.donation.DonationRequest;
import com.mgmtp.gives.dto.donation.VNPayRequest;
import com.mgmtp.gives.dto.donation.VNPayResponse;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DonationService {
    Donation createDonation(DonationRequest request, User user);
    List<Donation> getMyDonations(Long userId);
    List<Donation> getPublicDonationsByCampaignId(Long campaignId);
    Page<Donation> getAllDonations(DonationStatus status, DonationType type, Long campaignId, Pageable pageable);
    Donation confirmDonation(Long donationId, User admin);
    VNPayResponse createVNPayDonation(VNPayRequest request, User user);
    Donation confirmVNPayDonation(Long donationId);
    Donation hideDonationMessage(Long donationId, boolean hidden, User currentUser);
}
