package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingRecipientResponse;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignMeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/meeting-recipients")
@RequiredArgsConstructor
@Tag(name = "Campaign Meeting Recipients", description = "Endpoints for selecting campaign meeting invitation recipients")
public class CampaignMeetingRecipientController {
    private final CampaignMeetingService campaignMeetingService;

    @GetMapping
    @Operation(summary = "Get campaign members that can receive meeting invitations")
    public ApiResponse<List<CampaignMeetingRecipientResponse>> getMeetingRecipients(
            @PathVariable Long campaignId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.getMeetingRecipients(
                campaignId,
                userDetails.getUser()
        ));
    }
}
