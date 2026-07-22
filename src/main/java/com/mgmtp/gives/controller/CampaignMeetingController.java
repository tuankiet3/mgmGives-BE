package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingResponse;
import com.mgmtp.gives.dto.campaign_meeting.CampaignMeetingRecipientResponse;
import com.mgmtp.gives.dto.campaign_meeting.CreateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.MeetingActivityResponse;
import com.mgmtp.gives.dto.campaign_meeting.MeetingNotesResponse;
import com.mgmtp.gives.dto.campaign_meeting.UpdateCampaignMeetingRequest;
import com.mgmtp.gives.dto.campaign_meeting.UpdateMeetingNotesRequest;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignMeetingService;
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

import java.util.List;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Slf4j
@RestController
@RequestMapping("/api/campaigns/{campaignId}/meetings")
@RequiredArgsConstructor
@Tag(name = "Campaign Meetings", description = "Endpoints for scheduling and joining campaign Webex meetings")
public class CampaignMeetingController {
    private final CampaignMeetingService campaignMeetingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a Webex meeting for a campaign")
    public ApiResponse<CampaignMeetingResponse> createMeeting(
            @PathVariable Long campaignId,
            @Valid @RequestBody CreateCampaignMeetingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("REST request to create campaign meeting: campaignId={}, userId={}",
                campaignId, userDetails.getUser().getId());
        CampaignMeetingResponse response = campaignMeetingService.createMeeting(
                campaignId,
                request,
                userDetails.getUser()
        );
        return ApiResponse.success(response, "Campaign meeting created successfully");
    }

    @GetMapping
    @Operation(summary = "Get campaign meetings")
    public ApiResponse<List<CampaignMeetingResponse>> getMeetings(
            @PathVariable Long campaignId,
            @RequestParam(required = false) String view,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<CampaignMeetingResponse> response = campaignMeetingService.getMeetings(
                campaignId,
                view,
                userDetails.getUser()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{meetingId}")
    @Operation(summary = "Get campaign meeting details")
    public ApiResponse<CampaignMeetingResponse> getMeeting(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.getMeeting(
                campaignId,
                meetingId,
                userDetails.getUser()
        ));
    }

    @PatchMapping("/{meetingId}")
    @Operation(summary = "Update an upcoming campaign Webex meeting")
    public ApiResponse<CampaignMeetingResponse> updateMeeting(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @Valid @RequestBody UpdateCampaignMeetingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CampaignMeetingResponse response = campaignMeetingService.updateMeeting(
                campaignId,
                meetingId,
                request,
                userDetails.getUser()
        );
        return ApiResponse.success(response, "Campaign meeting updated successfully");
    }

    @GetMapping("/{meetingId}/invited-members")
    @Operation(summary = "Get invited members for a campaign meeting")
    public ApiResponse<List<CampaignMeetingRecipientResponse>> getInvitedMembers(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.getInvitedMembers(
                campaignId,
                meetingId,
                userDetails.getUser()
        ));
    }

    @GetMapping("/{meetingId}/notes")
    @Operation(summary = "Get campaign meeting notes")
    public ApiResponse<MeetingNotesResponse> getMeetingNotes(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.getMeetingNotes(
                campaignId,
                meetingId,
                userDetails.getUser()
        ));
    }

    @PatchMapping("/{meetingId}/notes")
    @Operation(summary = "Update campaign meeting notes")
    public ApiResponse<MeetingNotesResponse> updateMeetingNotes(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @Valid @RequestBody UpdateMeetingNotesRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.updateMeetingNotes(
                campaignId,
                meetingId,
                request,
                userDetails.getUser()
        ), "Campaign meeting notes updated successfully");
    }

    @GetMapping("/{meetingId}/activity")
    @Operation(summary = "Get campaign meeting activity")
    public ApiResponse<List<MeetingActivityResponse>> getMeetingActivity(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.getMeetingActivity(
                campaignId,
                meetingId,
                userDetails.getUser()
        ));
    }

    @GetMapping("/{meetingId}/attachments")
    @Operation(summary = "Get campaign meeting attachments")
    public ApiResponse<List<CampaignMediaResponse>> getMeetingAttachments(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ApiResponse.success(campaignMeetingService.getMeetingAttachments(
                campaignId,
                meetingId,
                userDetails.getUser()
        ));
    }

    @PostMapping(value = "/{meetingId}/attachments", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a campaign meeting attachment")
    public ApiResponse<CampaignMediaResponse> uploadMeetingAttachment(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CampaignMediaResponse response = campaignMeetingService.uploadMeetingAttachment(
                campaignId,
                meetingId,
                file,
                userDetails.getUser()
        );
        return ApiResponse.success(response, "Campaign meeting attachment uploaded successfully");
    }

    @DeleteMapping("/{meetingId}/attachments/{attachmentId}")
    @Operation(summary = "Delete a campaign meeting attachment")
    public ApiResponse<CampaignMediaResponse> deleteMeetingAttachment(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CampaignMediaResponse response = campaignMeetingService.deleteMeetingAttachment(
                campaignId,
                meetingId,
                attachmentId,
                userDetails.getUser()
        );
        return ApiResponse.success(response, "Campaign meeting attachment deleted successfully");
    }

    @PatchMapping("/{meetingId}/cancel")
    @Operation(summary = "Cancel a campaign Webex meeting")
    public ApiResponse<CampaignMeetingResponse> cancelMeeting(
            @PathVariable Long campaignId,
            @PathVariable Long meetingId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("REST request to cancel campaign meeting: campaignId={}, meetingId={}, userId={}",
                campaignId, meetingId, userDetails.getUser().getId());
        CampaignMeetingResponse response = campaignMeetingService.cancelMeeting(
                campaignId,
                meetingId,
                userDetails.getUser()
        );
        return ApiResponse.success(response, "Campaign meeting cancelled successfully");
    }

}
