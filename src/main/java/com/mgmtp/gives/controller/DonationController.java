package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.donation.DonationRequest;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.dto.donation.VNPayRequest;
import com.mgmtp.gives.dto.donation.VNPayResponse;
import com.mgmtp.gives.entity.Donation;
import lombok.extern.slf4j.Slf4j;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

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
    @Operation(summary = "Get public donations for a campaign", description = "Retrieve a list of public donations (excluding rejected and failed ones) for a specific campaign.")
    public ApiResponse<List<DonationResponse>> getPublicCampaignDonations(@RequestParam Long campaignId) {
        List<DonationResponse> responseList = donationService.getPublicDonationsByCampaignId(campaignId);
        return ApiResponse.success(responseList);
    }

    @PostMapping("/vnpay/create")
    @Operation(summary = "Create a new VNPay donation request", description = "Generates a mock VNPay QR code for money donation and creates a PENDING donation record in the database.")
    public ApiResponse<VNPayResponse> createVNPayDonation(@Valid @RequestBody VNPayRequest request,
                                                          @AuthenticationPrincipal CustomUserDetails userDetails) {
        VNPayResponse response = donationService.createVNPayDonation(request, userDetails.getUser());
        return ApiResponse.success(response, "VNPay QR generated successfully");
    }

    @PostMapping("/vnpay/callback/{id}")
    @Operation(summary = "Simulate VNPay IPN Callback", description = "Simulates the VNPay secure IPN callback confirming successful payment, changing status to CONFIRMED and broadcasting via WebSocket.")
    public ApiResponse<DonationResponse> simulateVNPayCallback(@PathVariable Long id) {
        DonationResponse donation = donationService.confirmVNPayDonation(id);
        return ApiResponse.success(donation, "VNPay payment simulated successfully");
    }

    @PostMapping("/vnpay/cancel/{id}")
    @Operation(summary = "Cancel VNPay donation request", description = "Marks a PENDING VNPay donation request as FAILED (e.g. if the user cancels or it times out).")
    public ApiResponse<DonationResponse> cancelVNPayDonation(@PathVariable Long id) {
        DonationResponse donation = donationService.cancelVNPayDonation(id);
        return ApiResponse.success(donation, "Donation request cancelled");
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
