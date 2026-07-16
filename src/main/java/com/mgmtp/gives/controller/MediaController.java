package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
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
    @Operation(summary = "Upload campaign media", description = "Uploads an image, video, or PDF document for a campaign. Accepts image/jpeg, image/png, image/webp, image/gif (max 15MB); video/mp4, video/quicktime, video/x-msvideo, video/webm (max 200MB); application/pdf (max 10MB). Only image files can be set as cover. Optional `context` (CAMPAIGN or FINAL_REPORT, defaults to CAMPAIGN) excludes the media from the general campaign gallery when set to FINAL_REPORT.")
    public ApiResponse<CampaignMediaResponse> uploadCampaignMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam("campaignId") Long campaignId,
            @RequestParam(value = "isCover", required = false, defaultValue = "false") boolean isCover,
            @RequestParam(value = "context", required = false) String context,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CampaignMediaResponse media = mediaService.uploadCampaignMedia(file, campaignId, isCover, context, userDetails.getUser());
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
    public ApiResponse<CampaignMediaResponse> softDeleteCampaignMedia(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CampaignMediaResponse media = mediaService.softDeleteCampaignMedia(id, userDetails.getUser());
        return ApiResponse.success(media, "Campaign media deleted successfully");
    }

    @PostMapping(value = "/upload/avatar", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload user avatar", description = "Uploads an image file and updates the current user's avatar_url. Accepts image/jpeg, image/png, image/webp (max 15MB). Videos are rejected.")
    public ApiResponse<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String filename = mediaService.uploadAvatar(file, userDetails.getUser());
        return ApiResponse.success(filename, "Avatar uploaded successfully");
    }

    @PostMapping(value = "/upload/campaign-qr", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload campaign QR image", description = "Uploads an image file and returns the stored filename.")
    public ApiResponse<String> uploadCampaignQr(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String filename = mediaService.uploadCampaignQr(file, userDetails.getUser());
        return ApiResponse.success(filename, "Campaign QR uploaded successfully");
    }

    @PostMapping(value = "/upload/transaction-proof", consumes = MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload transaction proof image", description = "Uploads an image file for transaction verification and returns the stored filename.")
    public ApiResponse<String> uploadTransactionProof(
            @RequestParam("file") MultipartFile file) {
        String filename = mediaService.uploadTransactionProof(file);
        return ApiResponse.success(filename, "Transaction proof uploaded successfully");
    }

    @PatchMapping("/{id}/campaign/restore")
    @Operation(summary = "Restore campaign media", description = "Restores a soft-deleted campaign media record. Must be within 14 days of deletion.")
    public ApiResponse<CampaignMediaResponse> restoreCampaignMedia(@PathVariable Long id) {
        CampaignMedia media = mediaService.restoreCampaignMedia(id);
        CampaignMediaResponse response = CampaignMediaResponse.builder()
                .id(media.getId()).url(media.getUrl()).mediaType(media.getMediaType()).isCover(media.isCover())
                .caption(media.getCaption()).displayOrder(media.getDisplayOrder()).context(media.getContext().name())
                .build();
        return ApiResponse.success(response, "Campaign media restored successfully");
    }
}
