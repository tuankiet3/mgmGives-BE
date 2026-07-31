package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.donation.DonationRequest;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.dto.donation.PayOSRequest;
import com.mgmtp.gives.dto.donation.PayOSResponse;
import com.mgmtp.gives.dto.donation.RejectRequest;
import com.mgmtp.gives.dto.donation.EditDonationRequest;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.createDonation(request, userDetails.getUser(), idempotencyKey);
        return ApiResponse.success(donation, "Donation submitted successfully");
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's donations", description = "Retrieve a list of donations submitted by the current authenticated user.")
    public ApiResponse<PageResponse<DonationResponse>> getMyDonations(
            @RequestParam(required = false) DonationStatus status,
            @RequestParam(required = false) DonationType type,
            @RequestParam(required = false) Boolean anonymous,
            @RequestParam(required = false) String search,
            @org.springdoc.core.annotations.ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Page<DonationResponse> responsePage = donationService.getMyDonations(
                userDetails.getUser().getId(), status, type, anonymous, search, pageable);
        return ApiResponse.success(PageResponse.of(responsePage, responsePage.getContent()));
    }

    @GetMapping
    @Operation(summary = "Get public donations for a campaign", description = "Retrieve a list of public donations (excluding failed ones) for a specific campaign.")
    public ApiResponse<List<DonationResponse>> getPublicCampaignDonations(@RequestParam Long campaignId) {
        List<DonationResponse> responseList = donationService.getPublicDonationsByCampaignId(campaignId);
        return ApiResponse.success(responseList);
    }

    @GetMapping("/campaign/{campaignId}")
    @Operation(summary = "Get all donations for a campaign (for campaign admin)", description = "Retrieve a list of all donations for a specific campaign, including failed ones. Restricted to campaign creator/admin.")
    public ApiResponse<List<DonationResponse>> getCampaignDonationsForAdmin(@PathVariable Long campaignId,
                                                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<DonationResponse> responseList = donationService.getCampaignDonationsForAdmin(campaignId, userDetails.getUser());
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
    @Operation(summary = "Hide or show a donation message (moderation)", description = "Allows a Campaign Admin to moderate donation messages.")
    public ApiResponse<DonationResponse> hideDonationMessage(@PathVariable Long id,
                                                             @RequestParam boolean hidden,
                                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.hideDonationMessage(id, hidden, userDetails.getUser());
        return ApiResponse.success(donation, "Message status updated successfully");
    }

    @PatchMapping("/{id}/amount/visibility")
    @Operation(summary = "Toggle donation amount visibility", description = "Allows the donor or admin to toggle the visibility of the donation amount.")
    public ApiResponse<DonationResponse> toggleAmountVisibility(@PathVariable Long id,
                                                                @RequestParam boolean hidden,
                                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.toggleDonationAmountVisibility(id, hidden, userDetails.getUser());
        return ApiResponse.success(donation, "Donation amount visibility updated successfully");
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm a donation", description = "Allows a Campaign Admin to confirm a pending manual QR donation.")
    public ApiResponse<DonationResponse> confirmDonation(@PathVariable Long id,
                                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.confirmCampaignDonation(id, userDetails.getUser());
        return ApiResponse.success(donation, "Donation confirmed successfully");
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Reject a donation", description = "Allows a Campaign Admin to reject a pending manual QR donation with a reason.")
    public ApiResponse<DonationResponse> rejectDonation(@PathVariable Long id,
                                                        @Valid @RequestBody RejectRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.rejectCampaignDonation(id, request.reason(), userDetails.getUser());
        return ApiResponse.success(donation, "Donation rejected successfully");
    }

    @PatchMapping("/{id}/edit")
    @Operation(summary = "Edit a donation", description = "Allows a Campaign Admin to edit amount, description, or reject reason of a manual QR donation.")
    public ApiResponse<DonationResponse> editDonation(@PathVariable Long id,
                                                      @Valid @RequestBody EditDonationRequest request,
                                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.editCampaignDonation(id, request, userDetails.getUser());
        return ApiResponse.success(donation, "Donation details updated successfully");
    }

    @PatchMapping("/{id}/proof")
    @Operation(summary = "Submit transaction proof for a manual donation", description = "Submit/attach the receipt proof image URL for a manual QR transfer donation.")
    public ApiResponse<DonationResponse> submitProof(@PathVariable Long id,
                                                     @RequestParam(required = false) String proofUrl,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationResponse donation = donationService.submitManualProof(id, proofUrl, userDetails.getUser());
        return ApiResponse.success(donation, "Transaction proof receipt submitted successfully");
    }
}
