package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.dto.campaign_member.JoinedCampaignResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            @PageableDefault(size = 10, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long userId = customUserDetails.getUser().getId();
        PageResponse<JoinedCampaignResponse> result = service.getJoinedCampaigns(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
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
            description = "Allows the current user to unjoin a campaign. Not allowed if the user has been assigned tasks."
    )
    public ResponseEntity<Void> unjoinCampaign(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long campaignId) {

        service.unjoinCampaign(customUserDetails.getUser(), campaignId);
        return ResponseEntity.noContent().build();
    }
}