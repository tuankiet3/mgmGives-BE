package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media Management", description = "Endpoints for uploading and managing campaign media files")
public class MediaController {

    private final MediaService mediaService;

    @Value("${app.media.upload-dir}")
    private String uploadDir;

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

    @GetMapping("/{filename}")
    @Operation(summary = "Serve media file", description = "Returns the raw media file (image or video). Returns 404 if the file has been soft-deleted.")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolvedPath = uploadPath.resolve(filename).normalize();

        if (!resolvedPath.startsWith(uploadPath)) {
            log.warn("Path traversal attempt detected: filename={}", filename);
            throw new AppException(ErrorCode.PATH_TRAVERSAL_DETECTED);
        }

        mediaService.assertFileAccessible(filename);

        Resource resource;
        try {
            resource = new UrlResource(resolvedPath.toUri());
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to resolve file path");
        }

        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException(ErrorCode.MEDIA_NOT_FOUND, "File not found: " + filename);
        }

        String contentType;
        try {
            contentType = Files.probeContentType(resolvedPath);
        } catch (IOException e) {
            contentType = null;
        }
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(resource);
    }
}
