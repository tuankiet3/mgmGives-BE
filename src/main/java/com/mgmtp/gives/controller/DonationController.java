package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.donation.DonationRequest;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.dto.donation.PayOSRequest;
import com.mgmtp.gives.dto.donation.PayOSResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
@Tag(name = "Donation Management", description = "Endpoints for user donations submission and tracking")
public class DonationController {

    private final DonationService donationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a new donation", description = "Submit a MONEY or GOODS donation to a campaign.")
    public ApiResponse<DonationResponse> createDonation(@Valid @RequestBody DonationRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.createDonation(request, userDetails.getUser());
        return ApiResponse.success(donation, "Donation submitted successfully");
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's donations", description = "Retrieve a list of donations submitted by the current authenticated user.")
    public ApiResponse<List<DonationResponse>> getMyDonations(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<DonationResponse> responseList = donationService.getMyDonations(userDetails.getUser().getId());
        return ApiResponse.success(responseList);
    }

    @GetMapping
    @Operation(summary = "Get public donations for a campaign", description = "Retrieve a list of public donations (excluding failed ones) for a specific campaign.")
    public ApiResponse<List<DonationResponse>> getPublicCampaignDonations(@RequestParam Long campaignId) {
        List<DonationResponse> responseList = donationService.getPublicDonationsByCampaignId(campaignId);
        return ApiResponse.success(responseList);
    }

    @PostMapping("/payos/create")
    @Operation(summary = "Create a PayOS payment link", description = "Generates a real PayOS VietQR checkout link for a money donation and creates a PENDING donation record.")
    public ApiResponse<PayOSResponse> createPayOSDonation(@Valid @RequestBody PayOSRequest request,
                                                          @AuthenticationPrincipal CustomUserDetails userDetails) {
        PayOSResponse response = donationService.createPayOSDonation(request, userDetails.getUser());
        return ApiResponse.success(response, "PayOS payment link created successfully");
    }

    @PostMapping("/payos/cancel/{id}")
    @Operation(summary = "Cancel PayOS donation request", description = "Marks a PENDING PayOS donation as FAILED if the user cancels from the checkout page.")
    public ApiResponse<DonationResponse> cancelPayOSDonation(@PathVariable Long id) {
        DonationResponse donation = donationService.cancelPayOSDonation(id);
        return ApiResponse.success(donation, "Donation request cancelled");
    }

    @PostMapping("/payos/verify/{id}")
    @Operation(summary = "Verify PayOS donation request status", description = "Checks the PayOS server to verify if this donation was paid successfully.")
    public ApiResponse<DonationResponse> verifyPayOSDonation(@PathVariable Long id) {
        DonationResponse donation = donationService.verifyPayOSUserTransaction(id);
        return ApiResponse.success(donation, "Donation request verified successfully");
    }

    @PatchMapping("/{id}/message/hide")
    @Operation(summary = "Hide or show a donation message (moderation)", description = "Allows Campaign Admin or global ADMIN to moderate donation messages.")
    public ApiResponse<DonationResponse> hideDonationMessage(@PathVariable Long id,
                                                             @RequestParam boolean hidden,
                                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.hideDonationMessage(id, hidden, userDetails.getUser());
        return ApiResponse.success(donation, "Message status updated successfully");
    }
}
