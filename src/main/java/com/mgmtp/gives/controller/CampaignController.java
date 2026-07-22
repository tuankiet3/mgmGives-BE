package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
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
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign Management", description = "Endpoints for managing volunteering and donation campaigns")
public class CampaignController {

        private final CampaignService campaignService;

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create a new campaign", description = "Creates a campaign in PENDING status. Restricted to authenticated users.")
        public ApiResponse<CampaignResponse> createCampaign(
                        @Valid @RequestBody CampaignRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to create campaign: title={}, userId={}", request.title(),
                                userDetails.getUser().getId());
                Campaign campaign = campaignService.createCampaign(request, userDetails.getUser());
                return ApiResponse.success(campaignService.toResponse(campaign, userDetails.getUser()),
                                "Campaign created successfully");
        }

        @GetMapping
        @Operation(summary = "Get all campaigns", description = "Endpoint to retrieve campaigns matching optional query filters.")
        public ApiResponse<PageResponse<CampaignResponse>> getAllCampaigns(
                        @RequestParam(required = false) CampaignStatus status,
                        @RequestParam(required = false) CampaignPriority priority,
                        @RequestParam(value = "categoryId", required = false) List<Long> categoryIds,
                        @RequestParam(required = false) Long userId,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) Boolean isFollowing,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
                log.info("REST request to get campaigns list: status={}, priority={}, keyword={}, isFollowing={}", status, priority,
                                keyword, isFollowing);
                User currentUser = userDetails != null ? userDetails.getUser() : null;
                Page<Campaign> campaignPage = campaignService.getAllCampaigns(status, priority, categoryIds, userId,
                                keyword, isFollowing,
                                currentUser, pageable);
                List<CampaignResponse> dtoList = campaignService.toResponseList(campaignPage.getContent(),
                                currentUser);
                return ApiResponse.success(PageResponse.of(campaignPage, dtoList));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get campaign details by ID", description = "Restricted to authenticated users. Returns the campaign details if the user is an admin, the creator, or if the campaign is IN_PROGRESS or COMPLETED.")
        public ApiResponse<CampaignResponse> getCampaignById(
                        @Parameter(description = "The ID of the campaign", required = true) @PathVariable Long id,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                User currentUser = userDetails != null ? userDetails.getUser() : null;
                log.info("REST request to get campaign details: id={}, userId={}", id,
                                currentUser != null ? currentUser.getId() : "anonymous");
                Campaign campaign = campaignService.getCampaignById(id, currentUser);
                return ApiResponse.success(campaignService.toResponse(campaign, currentUser));
        }

        @PutMapping("/{id}")
        @Operation(summary = "Update campaign details", description = "Allows updating campaign information. Restricted to the Campaign Creator or users with the campaign_admin role for that specific campaign. Campaign must be in DRAFT, PENDING, or REJECTED status.")
        public ApiResponse<CampaignResponse> updateCampaign(
                        @Parameter(description = "The ID of the campaign to update", required = true) @PathVariable Long id,
                        @Valid @RequestBody CampaignRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to update campaign: id={}, userId={}", id, userDetails.getUser().getId());
                Campaign campaign = campaignService.updateCampaign(id, request, userDetails.getUser());
                return ApiResponse.success(campaignService.toResponse(campaign, userDetails.getUser()),
                                "Campaign updated successfully");
        }

        @PutMapping("/{id}/end")
        @Operation(summary = "End a campaign", description = "Ends an IN_PROGRESS campaign immediately, setting its status to COMPLETED. Restricted to the campaign's Campaign Admin (campaign_admin member role) only.")
        public ApiResponse<CampaignResponse> endCampaign(
                        @Parameter(description = "The ID of the campaign to end", required = true) @PathVariable Long id,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to end campaign: id={}, userId={}", id, userDetails.getUser().getId());
                Campaign campaign = campaignService.endCampaign(id, userDetails.getUser());
                return ApiResponse.success(campaignService.toResponse(campaign, userDetails.getUser()),
                                "Campaign ended successfully");
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Delete a campaign", description = "Removes a campaign by ID. Restricted to the Campaign Creator only. Campaign must be in PENDING, REJECTED, or DRAFT status.")
        public ApiResponse<Void> deleteCampaign(
                        @Parameter(description = "The ID of the campaign to delete", required = true) @PathVariable Long id,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to delete campaign: id={}, userId={}", id, userDetails.getUser().getId());
                campaignService.deleteCampaign(id, userDetails.getUser());
                return ApiResponse.success(null, "Campaign deleted successfully");
        }

        @PostMapping("/test/trigger-auto-start")
        @Operation(summary = "TEST ONLY: Trigger auto-start of campaigns starting today")
        public ApiResponse<Void> triggerAutoStart() {
                log.info("REST request to manually trigger auto-start of campaigns");
                campaignService.startApprovedCampaignsScheduled();
                return ApiResponse.success(null, "Campaigns auto-start triggered successfully");
        }

        @PostMapping("/test/trigger-auto-complete")
        @Operation(summary = "TEST ONLY: Trigger auto-complete of ended campaigns")
        public ApiResponse<Void> triggerAutoComplete() {
                log.info("REST request to manually trigger auto-complete of ended campaigns");
                campaignService.completeEndedCampaignsScheduled();
                return ApiResponse.success(null, "Campaigns auto-complete triggered successfully");
        }
}
