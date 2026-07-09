package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign_follower.FollowedCampaignResponse;
import com.mgmtp.gives.dto.campaign_follower.CampaignFollowerFilterCriteria;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignFollowerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campaigns/followed")
@RequiredArgsConstructor
@Tag(
        name = "Campaign Followers",
        description = "APIs for viewing and unfollowing campaigns followed by the current employee"
)
public class CampaignFollowerController {

    private final CampaignFollowerService service;

    @GetMapping
    @Operation(
            summary = "Get followed campaigns",
            description = "Returns the list of campaigns followed by the current logged-in employee."
    )
    public ResponseEntity<?> getFollowedCampaigns(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @ModelAttribute CampaignFollowerFilterCriteria criteria,
            @PageableDefault(size = 10, sort = "followedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long userId = customUserDetails.getUser().getId();

        PageResponse<FollowedCampaignResponse> result =
                service.getFollowedCampaigns(userId, criteria, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{campaignId}")
    @Operation(
            summary = "Unfollow a campaign",
            description = "Removes the campaign from the user's followed list."
    )
    public ResponseEntity<Void> unfollow(@AuthenticationPrincipal CustomUserDetails customUserDetails,
                                         @PathVariable Long campaignId) {

        Long userId = customUserDetails.getUser().getId();
        service.unfollow(userId, campaignId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{campaignId}")
    @Operation(
            summary = "Follow a campaign",
            description = "Adds the campaign to the user's followed list."
    )
    public ResponseEntity<Void> follow(@AuthenticationPrincipal CustomUserDetails customUserDetails,
                                       @PathVariable Long campaignId) {

        Long userId = customUserDetails.getUser().getId();
        service.manualFollow(userId, campaignId);
        return ResponseEntity.ok().build();
    }
}