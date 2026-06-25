package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign Management", description = "Endpoints for managing volunteering and donation campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignMapper campaignMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new campaign", description = "Creates a campaign in PENDING status. Restricted to authenticated users.")
    public ApiResponse<CampaignResponse> createCampaign(
            @Valid @RequestBody CampaignRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Campaign campaign = campaignService.createCampaign(request, userDetails.getUser());
        return ApiResponse.success(campaignMapper.toResponse(campaign), "Campaign created successfully");
    }

    @GetMapping
    @Operation(summary = "Get all campaigns", description = "Endpoint to retrieve campaigns matching optional query filters.")
    public ApiResponse<PageResponse<CampaignResponse>> getAllCampaigns(
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) CampaignPriority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Campaign> campaignPage = campaignService.getAllCampaigns(status, priority, categoryId, userId, keyword,
                userDetails.getUser(), pageable);
        List<CampaignResponse> dtoList = campaignPage.getContent().stream()
                .map(campaignMapper::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(PageResponse.of(campaignPage, dtoList));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get campaign details by ID", description = "Restricted to authenticated users. Returns the campaign details if the user is an admin, the creator, or if the campaign is APPROVED.")
    public ApiResponse<CampaignResponse> getCampaignById(
            @Parameter(description = "The ID of the campaign", required = true) @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails != null ? userDetails.getUser() : null;
        Campaign campaign = campaignService.getCampaignById(id, currentUser);
        return ApiResponse.success(campaignMapper.toResponse(campaign));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update campaign details", description = "Allows updating campaign information. Restricted to owner or ADMIN role.")
    public ApiResponse<CampaignResponse> updateCampaign(
            @Parameter(description = "The ID of the campaign to update", required = true) @PathVariable Long id,
            @Valid @RequestBody CampaignRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Campaign campaign = campaignService.updateCampaign(id, request, userDetails.getUser());
        return ApiResponse.success(campaignMapper.toResponse(campaign), "Campaign updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a campaign", description = "Removes a campaign by ID. Restricted to ADMIN role or the creator (for drafts).")
    public ApiResponse<Void> deleteCampaign(
            @Parameter(description = "The ID of the campaign to delete", required = true) @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        campaignService.deleteCampaign(id, userDetails.getUser());
        return ApiResponse.success(null, "Campaign deleted successfully");
    }
}
