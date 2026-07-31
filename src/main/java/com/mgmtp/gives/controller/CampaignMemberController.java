package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberFilterCriteria;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignRosterResponse;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.dto.campaign_member.RejectUnjoinRequest;
import com.mgmtp.gives.dto.campaign_member.UnjoinCampaignResponse;
import com.mgmtp.gives.dto.campaign_member.UnjoinRequestResponse;
import com.mgmtp.gives.dto.campaign_member.UpdateMemberHiddenRequest;
import com.mgmtp.gives.dto.campaign_member.UpdateRosterVisibilityRequest;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign Members", description = "APIs for joining campaigns as a volunteer")
public class CampaignMemberController {

    private final CampaignMemberService service;

    @GetMapping("/joined")
    @Operation(
            summary = "Get joined campaigns",
            description = "Returns the paginated list of campaigns the current user has joined as a volunteer."
    )
    public ResponseEntity<?> getJoinedCampaigns(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @ModelAttribute CampaignMemberFilterCriteria criteria,
            @PageableDefault(size = 10, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long userId = customUserDetails.getUser().getId();
        PageResponse<JoinedCampaignResponse> result = service.getJoinedCampaigns(userId, criteria, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{campaignId}/members")
    @Operation(
            summary = "Get the campaign volunteer roster",
            description = "Publicly accessible. Admins and members always receive the full list; " +
                    "other viewers receive it only when the campaign's roster visibility is PUBLIC " +
                    "(minus members who opted out), otherwise just the aggregate count."
    )
    public ResponseEntity<?> getCampaignRoster(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId) {

        CampaignRosterResponse result = service.getCampaignRoster(
                campaignId, customUserDetails != null ? customUserDetails.getUser() : null);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PatchMapping("/{campaignId}/members/visibility")
    @Operation(
            summary = "Update campaign roster visibility",
            description = "Campaign admin only. Sets whether the volunteer roster is PUBLIC or MEMBERS_ONLY."
    )
    public ResponseEntity<?> updateRosterVisibility(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId,
            @Valid @RequestBody UpdateRosterVisibilityRequest request) {

        service.updateRosterVisibility(campaignId, request.visibility(), customUserDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Roster visibility updated successfully"));
    }

    @PatchMapping("/{campaignId}/members/me/visibility")
    @Operation(
            summary = "Update own roster visibility",
            description = "Sets whether the current user's name is hidden from the campaign's public roster."
    )
    public ResponseEntity<?> updateOwnRosterVisibility(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId,
            @Valid @RequestBody UpdateMemberHiddenRequest request) {

        service.updateOwnRosterVisibility(campaignId, request.hidden(), customUserDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Roster preference updated successfully"));
    }

    @PostMapping("/{campaignId}/members")
    @Operation(
            summary = "Join a campaign",
            description = "Allows the current user to join a campaign as a volunteer."
    )
    public ResponseEntity<?> joinCampaign(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId) {

        CampaignMemberResponse result = service.joinCampaign(customUserDetails.getUser(), campaignId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @DeleteMapping("/{campaignId}/members")
    @Operation(
            summary = "Unjoin a campaign",
            description = "Unjoins immediately, unless the user still has a non-DONE assigned task in the campaign, " +
                    "in which case an unjoin request is created pending campaign admin approval."
    )
    public ResponseEntity<?> unjoinCampaign(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId) {

        UnjoinCampaignResponse result = service.unjoinCampaign(customUserDetails.getUser(), campaignId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{campaignId}/members/unjoin-request")
    @Operation(
            summary = "Cancel a pending unjoin request",
            description = "Allows the current user to cancel their own pending unjoin request before the admin responds."
    )
    public ResponseEntity<?> cancelUnjoinRequest(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId) {

        service.cancelUnjoinRequest(customUserDetails.getUser(), campaignId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{campaignId}/members/unjoin-requests")
    @Operation(
            summary = "List pending unjoin requests",
            description = "Campaign admin only. Returns the paginated list of members waiting for unjoin approval."
    )
    public ResponseEntity<?> getUnjoinRequests(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId,
            @PageableDefault(size = 10, sort = "unjoinRequestedAt", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<UnjoinRequestResponse> result =
                service.getUnjoinRequests(campaignId, customUserDetails.getUser(), pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{campaignId}/members/{userId}/unjoin-request/approve")
    @Operation(
            summary = "Approve a pending unjoin request",
            description = "Campaign admin only. Unassigns the member's tasks and removes them from the campaign."
    )
    public ResponseEntity<?> approveUnjoinRequest(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId,
            @PathVariable Long userId) {

        service.approveUnjoinRequest(campaignId, userId, customUserDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Unjoin request approved successfully"));
    }

    @PutMapping("/{campaignId}/members/{userId}/unjoin-request/reject")
    @Operation(
            summary = "Reject a pending unjoin request",
            description = "Campaign admin only. Requires a rejection reason; the member remains in the campaign."
    )
    public ResponseEntity<?> rejectUnjoinRequest(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId,
            @PathVariable Long userId,
            @Valid @RequestBody RejectUnjoinRequest request) {

        service.rejectUnjoinRequest(campaignId, userId, request.reason(), customUserDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Unjoin request rejected successfully"));
    }
}