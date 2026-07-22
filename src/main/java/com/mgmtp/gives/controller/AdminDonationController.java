package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.donation.DonationAdminResponse;
import com.mgmtp.gives.dto.donation.DonationRequest;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.web.PageableDefault;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/donations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Donation Management", description = "Endpoints for administrators to review and approve/reject donations")
public class AdminDonationController {

    private final DonationService donationService;

    @GetMapping
    @Operation(summary = "Get all donations with filters", description = "Retrieve all donations. Filter by status, type, campaign ID, and search keyword (donor name, campaign name, transaction ID).")
    public ApiResponse<PageResponse<DonationAdminResponse>> getAllDonations(
            @RequestParam(required = false) DonationStatus status,
            @RequestParam(required = false) DonationType type,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        log.info("Fetching admin donations: status={}, type={}, campaignId={}, search={}", status, type, campaignId, search);
        Page<DonationAdminResponse> donationPage = donationService.getAllDonations(status, type, campaignId, search, pageable);
                
        return ApiResponse.success(PageResponse.of(donationPage, donationPage.getContent()));
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm a donation", description = "Approve a pending donation.")
    public ApiResponse<DonationAdminResponse> confirmDonation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        DonationAdminResponse donation = donationService.confirmDonation(id, userDetails.getUser());
        return ApiResponse.success(donation, "Donation confirmed successfully");
    }
}
