package com.mgmtp.gives.dto.campaign_task;

import java.time.LocalDateTime;

public record TaskAttachmentResponse(
        Long id,
        String originalFilename,
        String url,
        String fileType,
        Long fileSize,
        UploadedByInfo uploadedBy,
        LocalDateTime uploadedAt
) {
    public record UploadedByInfo(
            Long id,
            String fullName,
            String email,
            String avatarUrl
    ) {
    }
}
