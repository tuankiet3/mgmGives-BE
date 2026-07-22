package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingListResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingResponse;
import com.mgmtp.gives.dto.campaign_spending.CreateCampaignSpendingRequest;
import com.mgmtp.gives.dto.campaign_spending.UpdateCampaignSpendingRequest;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignSpendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Campaign Spending", description = "Endpoints for tracking money spent on campaign activities")
public class CampaignSpendingController {

    private final CampaignSpendingService campaignSpendingService;

    @PostMapping("/campaigns/{campaignId}/spendings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a new spending entry for a campaign")
    public ApiResponse<CampaignSpendingResponse> createSpending(
            @PathVariable Long campaignId,
            @Valid @RequestBody CreateCampaignSpendingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to create spending: campaignId={}, userId={}",
                campaignId, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignSpendingService.createSpending(campaignId, request, userDetails.getUser()),
                "Spending logged successfully");
    }

    @GetMapping("/campaigns/{campaignId}/spendings")
    @Operation(summary = "Get spending entries and totals for a campaign")
    public ApiResponse<CampaignSpendingListResponse> getSpendings(@PathVariable Long campaignId) {
        return ApiResponse.success(campaignSpendingService.getSpendingsByCampaign(campaignId));
    }

    @PatchMapping("/spendings/{spendingId}")
    @Operation(summary = "Update a spending entry")
    public ApiResponse<CampaignSpendingResponse> updateSpending(
            @PathVariable Long spendingId,
            @Valid @RequestBody UpdateCampaignSpendingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to update spending: spendingId={}, userId={}",
                spendingId, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignSpendingService.updateSpending(spendingId, request, userDetails.getUser()),
                "Spending updated successfully");
    }

    @DeleteMapping("/spendings/{spendingId}")
    @Operation(summary = "Delete a spending entry")
    public ApiResponse<Void> deleteSpending(
            @PathVariable Long spendingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to delete spending: spendingId={}, userId={}",
                spendingId, userDetails.getUser().getId());
        campaignSpendingService.deleteSpending(spendingId, userDetails.getUser());
        return ApiResponse.success(null, "Spending deleted successfully");
    }

    @PostMapping(value = "/spendings/{spendingId}/photos", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a supporting photo for a spending entry")
    public ApiResponse<CampaignSpendingResponse> addPhoto(
            @PathVariable Long spendingId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to add spending photo: spendingId={}, userId={}",
                spendingId, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignSpendingService.addPhoto(spendingId, file, userDetails.getUser()),
                "Photo uploaded successfully");
    }

    @DeleteMapping("/spendings/{spendingId}/photos/{mediaId}")
    @Operation(summary = "Remove a supporting photo from a spending entry")
    public ApiResponse<CampaignSpendingResponse> removePhoto(
            @PathVariable Long spendingId,
            @PathVariable Long mediaId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to remove spending photo: spendingId={}, mediaId={}, userId={}",
                spendingId, mediaId, userDetails.getUser().getId());
        return ApiResponse.success(
                campaignSpendingService.removePhoto(spendingId, mediaId, userDetails.getUser()),
                "Photo removed successfully");
    }
}
