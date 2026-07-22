package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.announcement.*;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.AnnouncementLikeService;
import com.mgmtp.gives.service.AnnouncementReplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/campaigns/{campaignId}/announcements/{announcementId}")
@RequiredArgsConstructor
@Tag(name = "Announcement Engagement", description = "Endpoints for announcement likes and replies")
public class AnnouncementEngagementController {

    private final AnnouncementLikeService likeService;
    private final AnnouncementReplyService replyService;

    @PostMapping("/like")
    @Operation(summary = "Like an announcement")
    public ApiResponse<Void> like(
            @PathVariable Long campaignId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long announcementId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to like announcement: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, userDetails.getUser().getId());
        likeService.likeAnnouncement(campaignId, announcementId, userDetails.getUser());
        return ApiResponse.success(null, "Announcement liked successfully");
    }

    @DeleteMapping("/like")
    @Operation(summary = "Unlike an announcement")
    public ApiResponse<Void> unlike(
            @PathVariable Long campaignId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long announcementId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to unlike announcement: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, userDetails.getUser().getId());
        likeService.unlikeAnnouncement(campaignId, announcementId, userDetails.getUser());
        return ApiResponse.success(null, "Announcement unliked successfully");
    }

    @PostMapping("/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a reply to an announcement")
    public ApiResponse<AnnouncementReplyResponse> createReply(
            @PathVariable Long campaignId,
            @PathVariable Long announcementId,
            @Valid @RequestBody CreateReplyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to add reply: campaignId={}, announcementId={}, userId={}",
                campaignId, announcementId, userDetails.getUser().getId());
        return ApiResponse.success(
                replyService.createReply(campaignId, announcementId, request, userDetails.getUser()),
                "Reply added successfully"
        );
    }

    @PutMapping("/replies/{replyId}")
    @Operation(summary = "Update a reply")
    public ApiResponse<AnnouncementReplyResponse> updateReply(
            @PathVariable Long campaignId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long announcementId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long replyId,
            @Valid @RequestBody UpdateReplyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to update reply: replyId={}, userId={}", replyId, userDetails.getUser().getId());
        return ApiResponse.success(
                replyService.updateReply(campaignId, announcementId, replyId, request, userDetails.getUser()),
                "Reply updated successfully"
        );
    }

    @DeleteMapping("/replies/{replyId}")
    @Operation(summary = "Delete a reply")
    public ApiResponse<Void> deleteReply(
            @PathVariable Long campaignId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long announcementId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long replyId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to delete reply: replyId={}, userId={}", replyId, userDetails.getUser().getId());
        replyService.deleteReply(campaignId, announcementId, replyId, userDetails.getUser());
        return ApiResponse.success(null, "Reply deleted successfully");
    }

    @GetMapping("/replies")
    @Operation(summary = "Get announcement replies (keyset paginated)")
    public ApiResponse<ReplyPageResponse<AnnouncementReplyResponse>> getReplies(
            @PathVariable Long campaignId, // Required by Spring MVC to map the path variable from the URL structure
            @PathVariable Long announcementId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "15") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 50, message = "Limit cannot exceed 50") int limit,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "(?i)asc|desc", message = "Sort must be asc or desc") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to get replies: announcementId={}, cursor={}, limit={}, sort={}",
                announcementId, cursor, limit, sort);
        return ApiResponse.success(replyService.getReplies(campaignId, announcementId, cursor, limit, sort, userDetails.getUser()));
    }

    @GetMapping("/replies/{replyId}/context")
    @Operation(summary = "Get a reply-centered announcement context")
    public ApiResponse<ReplyContextResponse> getReplyContext(
            @PathVariable Long campaignId,
            @PathVariable Long announcementId,
            @PathVariable Long replyId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) @Pattern(regexp = "(?i)newer|older", message = "Direction must be newer or older") String direction,
            @RequestParam(defaultValue = "15") @Min(value = 1, message = "Limit must be at least 1") @Max(value = 50, message = "Limit cannot exceed 50") int limit,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "(?i)asc|desc", message = "Sort must be asc or desc") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(replyService.getReplyContext(
                campaignId, announcementId, replyId, cursor, direction, limit, sort, userDetails.getUser()
        ));
    }
}
