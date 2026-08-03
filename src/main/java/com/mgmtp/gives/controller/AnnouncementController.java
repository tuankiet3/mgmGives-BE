package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.announcement.AnnouncementResponse;
import com.mgmtp.gives.dto.announcement.CreateAnnouncementRequest;
import com.mgmtp.gives.dto.announcement.UpdateAnnouncementRequest;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/campaigns/{campaignId}/announcements")
@RequiredArgsConstructor
@Tag(name = "Campaign Announcements", description = "Endpoints for campaign announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create campaign announcement")
    public ApiResponse<AnnouncementResponse> createAnnouncement(
            @PathVariable Long campaignId,
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to create announcement: campaignId={}, userId={}",
                campaignId, userDetails.getUser().getId());
        return ApiResponse.success(
                announcementService.createAnnouncement(campaignId, request, userDetails.getUser()),
                "Announcement created successfully");
    }

    @PutMapping("/{announcementId}")
    @Operation(summary = "Update campaign announcement")
    public ApiResponse<AnnouncementResponse> updateAnnouncement(
            @PathVariable Long campaignId,
            @PathVariable Long announcementId,
            @Valid @RequestBody UpdateAnnouncementRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to update announcement: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, userDetails.getUser().getId());
        return ApiResponse.success(
                announcementService.updateAnnouncement(campaignId, announcementId, request, userDetails.getUser()),
                "Announcement updated successfully");
    }

    @DeleteMapping("/{announcementId}")
    @Operation(summary = "Delete campaign announcement")
    public ApiResponse<Void> deleteAnnouncement(
            @PathVariable Long campaignId,
            @PathVariable Long announcementId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to delete announcement: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, userDetails.getUser().getId());
        announcementService.deleteAnnouncement(campaignId, announcementId, userDetails.getUser());
        return ApiResponse.success(null, "Announcement deleted successfully");
    }

    @GetMapping
    @Operation(summary = "Get campaign announcements")
    public ApiResponse<Page<AnnouncementResponse>> getAnnouncements(
            @PathVariable Long campaignId,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST request to get announcements: campaignId={}, pageable={}", campaignId, pageable);
        return ApiResponse.success(announcementService.getAnnouncementsByCampaign(campaignId, pageable));
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "Get campaign announcement")
    public ApiResponse<AnnouncementResponse> getAnnouncement(
            @PathVariable Long campaignId,
            @PathVariable Long announcementId) {
        log.info("REST request to get announcement: campaignId={}, announcementId={}", campaignId, announcementId);
        return ApiResponse.success(announcementService.getAnnouncementById(campaignId, announcementId));
    }

}
