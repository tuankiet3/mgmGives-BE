package com.mgmtp.gives.util;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.exception.AppException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

public final class MediaValidationUtil {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/webm"
    );

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 50L * 1024 * 1024;

    private MediaValidationUtil() {}

    public static String detectCategory(String contentType) {
        if (IMAGE_TYPES.contains(contentType)) return "IMAGE";
        if (VIDEO_TYPES.contains(contentType)) return "VIDEO";
        throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    public static void validateFile(MultipartFile file, boolean imageOnly) {
        String contentType = file.getContentType();
        String category = detectCategory(contentType);

        if (imageOnly && "VIDEO".equals(category)) {
            throw new AppException(ErrorCode.IMAGE_ONLY);
        }

        long maxSize = "IMAGE".equals(category) ? MAX_IMAGE_SIZE : MAX_VIDEO_SIZE;
        if (file.getSize() > maxSize) {
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        if ("IMAGE".equals(category)) {
            validateImageMagicBytes(file, contentType);
        }
    }

    private static void validateImageMagicBytes(MultipartFile file, String contentType) {
        try {
            byte[] header = file.getInputStream().readNBytes(12);
            boolean valid = switch (contentType) {
                case "image/jpeg" -> header.length >= 3
                        && header[0] == (byte) 0xFF
                        && header[1] == (byte) 0xD8
                        && header[2] == (byte) 0xFF;
                case "image/png"  -> header.length >= 4
                        && header[0] == (byte) 0x89
                        && header[1] == 0x50
                        && header[2] == 0x4E
                        && header[3] == 0x47;
                case "image/webp" -> header.length >= 12
                        && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                        && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
                default -> false;
            };
            if (!valid) {
                throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
            }
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to read file content");
        }
    }
}
