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
    public ApiResponse<DonationResponse> createDonation(
            @Valid @RequestBody DonationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Donation donation = donationService.createDonation(request, userDetails.getUser());
        return ApiResponse.success(toResponse(donation), "Donation submitted successfully");
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's donations", description = "Retrieve a list of donations submitted by the current authenticated user.")
    public ApiResponse<List<DonationResponse>> getMyDonations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Donation> donations = donationService.getMyDonations(userDetails.getUser().getId());
        List<DonationResponse> responseList = donations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responseList);
    }

    @GetMapping
    @Operation(summary = "Get public donations for a campaign", description = "Retrieve a list of public donations (excluding rejected and failed ones) for a specific campaign.")
    public ApiResponse<List<DonationResponse>> getPublicCampaignDonations(@RequestParam Long campaignId) {
        List<Donation> donations = donationService.getPublicDonationsByCampaignId(campaignId);
        List<DonationResponse> responseList = donations.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responseList);
    }

    @PostMapping("/vnpay/create")
    @Operation(summary = "Create a new VNPay donation request", description = "Generates a mock VNPay QR code for money donation and creates a PENDING donation record in the database.")
    public ApiResponse<VNPayResponse> createVNPayDonation(
            @Valid @RequestBody VNPayRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Generating VNPay QR for user: {}, campaign: {}, amount: {} VND", 
                userDetails.getUser().getEmail(), request.campaignId(), request.amount());
        VNPayResponse response = donationService.createVNPayDonation(request, userDetails.getUser());
        return ApiResponse.success(response, "VNPay QR generated successfully");
    }

    @PostMapping("/vnpay/callback/{id}")
    @Operation(summary = "Simulate VNPay IPN Callback", description = "Simulates the VNPay secure IPN callback confirming successful payment, changing status to CONFIRMED and broadcasting via WebSocket.")
    public ApiResponse<DonationResponse> simulateVNPayCallback(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Simulating VNPay payment callback for donation ID: {} by user: {}", id, userDetails.getUser().getEmail());
        Donation donation = donationService.confirmVNPayDonation(id);
        return ApiResponse.success(toResponse(donation), "VNPay payment simulated successfully");
    }

    @PatchMapping("/{id}/message/hide")
    @Operation(summary = "Hide or show a donation message (moderation)", description = "Allows Campaign Admin or global ADMIN to moderate donation messages.")
    public ApiResponse<DonationResponse> hideDonationMessage(
            @PathVariable Long id,
            @RequestParam boolean hidden,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("User {} requested to set message hidden={} for donation ID: {}", 
                userDetails.getUser().getEmail(), hidden, id);
        Donation donation = donationService.hideDonationMessage(id, hidden, userDetails.getUser());
        return ApiResponse.success(toResponse(donation), "Message status updated successfully");
    }

    private DonationResponse toResponse(Donation donation) {
        String donorName = donation.isAnonymous() ? "Anonymous" : donation.getUser().getFullName();
        
        // Check if the current user is Campaign Admin or global ADMIN to display hidden message text
        boolean canSeeHidden = false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            com.mgmtp.gives.entity.User currentUser = userDetails.getUser();
            boolean isAdmin = currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;
            boolean isCreator = donation.getCampaign().getUser() != null && 
                                donation.getCampaign().getUser().getId().equals(currentUser.getId());
            if (isAdmin || isCreator) {
                canSeeHidden = true;
            }
        }
        
        String displayedMessage = donation.getMessage();
        if (donation.isMessageHidden() && !canSeeHidden) {
            displayedMessage = null;
        }

        return DonationResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .donorName(donorName)
                .type(donation.getType())
                .amount(donation.getAmount())
                .detail(donation.getDetail())
                .isAnonymous(donation.isAnonymous())
                .status(donation.getStatus())
                .transactionId(donation.getTransactionId())
                .rejectReason(donation.getRejectReason())
                .message(displayedMessage)
                .isMessageHidden(donation.isMessageHidden())
                .goodsCondition(donation.getGoodsCondition())
                .goodsCategory(donation.getGoodsCategory())
                .deliveryMethod(donation.getDeliveryMethod())
                .createdAt(donation.getCreatedAt())
                .build();
    }
}
