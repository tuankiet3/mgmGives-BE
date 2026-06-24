package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media Management", description = "Endpoints for uploading and managing campaign media files")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload/campaign", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload campaign media", description = "Uploads an image or video file for a campaign. Accepts image/jpeg, image/png, image/webp (max 5MB) and video/mp4, video/quicktime, video/x-msvideo, video/webm (max 50MB).")
    public ApiResponse<CampaignMedia> uploadCampaignMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("campaignId") Long campaignId) {
        CampaignMedia media = mediaService.uploadCampaignMedia(file, campaignId);
        return ApiResponse.success(media, "Campaign media uploaded successfully");
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Delete user avatar", description = "Permanently deletes the current user's avatar file from disk and clears avatar_url.")
    public ApiResponse<Void> deleteAvatar(@AuthenticationPrincipal CustomUserDetails userDetails) {
        mediaService.deleteAvatar(userDetails.getUser());
        return ApiResponse.success(null, "Avatar deleted successfully");
    }

    @DeleteMapping("/{id}/campaign")
    @Operation(summary = "Soft delete campaign media", description = "Marks a campaign media record as deleted. The file remains on disk and can be restored within 14 days.")
    public ApiResponse<CampaignMedia> softDeleteCampaignMedia(@PathVariable Long id) {
        CampaignMedia media = mediaService.softDeleteCampaignMedia(id);
        return ApiResponse.success(media, "Campaign media deleted successfully");
    }

    @PostMapping(value = "/upload/avatar", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload user avatar", description = "Uploads an image file and updates the current user's avatar_url. Accepts image/jpeg, image/png, image/webp (max 5MB). Videos are rejected.")
    public ApiResponse<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String filename = mediaService.uploadAvatar(file, userDetails.getUser());
        return ApiResponse.success(filename, "Avatar uploaded successfully");
    }

    @PatchMapping("/{id}/campaign/restore")
    @Operation(summary = "Restore campaign media", description = "Restores a soft-deleted campaign media record. Must be within 14 days of deletion.")
    public ApiResponse<CampaignMedia> restoreCampaignMedia(@PathVariable Long id) {
        CampaignMedia media = mediaService.restoreCampaignMedia(id);
        return ApiResponse.success(media, "Campaign media restored successfully");
    }
}
