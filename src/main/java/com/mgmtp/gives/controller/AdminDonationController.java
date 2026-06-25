package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.donation.DonationAdminResponse;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Admin Donation Management", description = "Endpoints for administrators to review and approve/reject donations")
public class AdminDonationController {

    private final DonationService donationService;

    @GetMapping
    @Operation(summary = "Get all donations with filters", description = "Retrieve all donations. Filter by status, type, and campaign ID.")
    public ApiResponse<PageResponse<DonationAdminResponse>> getAllDonations(
            @RequestParam(required = false) DonationStatus status,
            @RequestParam(required = false) DonationType type,
            @RequestParam(required = false) Long campaignId,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<Donation> donationPage = donationService.getAllDonations(status, type, campaignId, pageable);
        List<DonationAdminResponse> dtoList = donationPage.getContent().stream()
                .map(this::toAdminResponse)
                .collect(Collectors.toList());
                
        return ApiResponse.success(PageResponse.of(donationPage, dtoList));
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm a donation", description = "Approve a pending donation.")
    public ApiResponse<DonationAdminResponse> confirmDonation(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Donation donation = donationService.confirmDonation(id, userDetails.getUser());
        return ApiResponse.success(toAdminResponse(donation), "Donation confirmed successfully");
    }



    private DonationAdminResponse toAdminResponse(Donation donation) {
        return DonationAdminResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .userId(donation.getUser().getId())
                .userName(donation.getUser().getFullName())
                .userEmail(donation.getUser().getEmail())
                .type(donation.getType())
                .amount(donation.getAmount())
                .detail(donation.getDetail())
                .isAnonymous(donation.isAnonymous())
                .status(donation.getStatus())
                .transactionId(donation.getTransactionId())
                .confirmedById(donation.getConfirmedBy() != null ? donation.getConfirmedBy().getId() : null)
                .confirmedByName(donation.getConfirmedBy() != null ? donation.getConfirmedBy().getFullName() : null)
                .confirmedAt(donation.getConfirmedAt())
                .rejectReason(donation.getRejectReason())
                .message(donation.getMessage())
                .isMessageHidden(donation.isMessageHidden())
                .goodsCondition(donation.getGoodsCondition())
                .goodsCategory(donation.getGoodsCategory())
                .deliveryMethod(donation.getDeliveryMethod())
                .createdAt(donation.getCreatedAt())
                .updatedAt(donation.getUpdatedAt())
                .build();
    }
}
