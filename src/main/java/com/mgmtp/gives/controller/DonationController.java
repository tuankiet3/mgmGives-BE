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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/donations")
@RequiredArgsConstructor
@Tag(name = "Donation Management", description = "Endpoints for user donations submission and tracking")
public class DonationController {

    private final DonationService donationService;
    private final PayOS payOS;

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

    @PostMapping("/payos/webhook")
    @Operation(summary = "PayOS Webhook (IPN)", description = "Receives and verifies the PayOS IPN payment callback. Confirms the donation automatically on successful payment.")
    public ResponseEntity<Map<String, Object>> handlePayOSWebhook(@RequestBody Map<String, Object> webhookBody) {
        try {
            log.info("Received PayOS webhook request: {}", webhookBody);

            // Check if it is PayOS's webhook registration test payload
            if (webhookBody != null && (webhookBody.get("data") == null || "Ok".equals(webhookBody.get("message")))) {
                log.info("Received PayOS webhook validation test request. Returning 200 OK.");
                java.util.Map<String, Object> testResponse = new java.util.HashMap<>();
                testResponse.put("error", 0);
                testResponse.put("message", "Ok");
                testResponse.put("data", null);
                return ResponseEntity.ok(testResponse);
            }

            WebhookData data = payOS.webhooks().verify(webhookBody);
            log.info("PayOS webhook received for orderCode: {}, code: {}", data.getOrderCode(), data.getCode());

            if ("00".equals(data.getCode())) {
                Long donationId = data.getOrderCode();
                if (donationId != null && donationId == 123L) {
                    log.info("Received PayOS webhook validation check (orderCode = 123). Bypassing database lookup.");
                } else {
                    donationService.confirmPayOSDonation(donationId);
                    log.info("Donation ID {} confirmed via PayOS webhook.", donationId);
                }
            } else {
                log.warn("PayOS webhook received non-success code: {}", data.getCode());
            }

            java.util.Map<String, Object> successResponse = new java.util.HashMap<>();
            successResponse.put("error", 0);
            successResponse.put("message", "Ok");
            successResponse.put("data", null);
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            log.error("PayOS webhook verification failed: {}", e.getMessage(), e);
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", -1);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("data", null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/payos/cancel/{id}")
    @Operation(summary = "Cancel PayOS donation request", description = "Marks a PENDING PayOS donation as FAILED if the user cancels from the checkout page.")
    public ApiResponse<DonationResponse> cancelPayOSDonation(@PathVariable Long id) {
        DonationResponse donation = donationService.cancelPayOSDonation(id);
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
