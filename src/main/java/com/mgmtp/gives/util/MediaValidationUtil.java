package com.mgmtp.gives.util;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.exception.AppException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

public final class MediaValidationUtil {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/webm"
    );

    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain"
    );

    /** Document types accepted for general campaign media (a stricter subset of DOCUMENT_TYPES, task attachments allow more). */
    private static final Set<String> CAMPAIGN_DOCUMENT_TYPES = Set.of("application/pdf");

    private static final long MAX_IMAGE_SIZE = 15L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 200L * 1024 * 1024;
    private static final long MAX_DOCUMENT_SIZE = 10L * 1024 * 1024;

    private MediaValidationUtil() {}

    public static String detectCategory(String contentType) {
        if (IMAGE_TYPES.contains(contentType)) return "IMAGE";
        if (VIDEO_TYPES.contains(contentType)) return "VIDEO";
        if (CAMPAIGN_DOCUMENT_TYPES.contains(contentType)) return "DOCUMENT";
        throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    public static String detectTaskFileCategory(String contentType) {
        if (IMAGE_TYPES.contains(contentType)) return "IMAGE";
        if (VIDEO_TYPES.contains(contentType)) return "VIDEO";
        if (DOCUMENT_TYPES.contains(contentType)) return "DOCUMENT";
        throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    public static void validateFile(MultipartFile file, boolean imageOnly) {
        String contentType = file.getContentType();
        String category = detectCategory(contentType);

        if (imageOnly && !"IMAGE".equals(category)) {
            throw new AppException(ErrorCode.IMAGE_ONLY);
        }

        validateCampaignFileExtension(file.getOriginalFilename(), category);

        long maxSize = switch (category) {
            case "IMAGE" -> MAX_IMAGE_SIZE;
            case "DOCUMENT" -> MAX_DOCUMENT_SIZE;
            default -> MAX_VIDEO_SIZE;
        };
        if (file.getSize() > maxSize) {
            String message = switch (category) {
                case "IMAGE" -> "Image size must be less than 15MB";
                case "DOCUMENT" -> "Document size must be less than 10MB";
                default -> "Video size must be less than 200MB";
            };
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED, message);
        }

        if ("IMAGE".equals(category)) {
            validateImageMagicBytes(file, contentType);
        } else if ("DOCUMENT".equals(category)) {
            validatePdfMagicBytes(file);
        }
    }

    private static void validateCampaignFileExtension(String originalFilename, String category) {
        int lastDot = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        if (lastDot == -1) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        String ext = originalFilename.substring(lastDot + 1).toLowerCase();
        boolean validExt = switch (category) {
            case "IMAGE" -> Set.of("jpg", "jpeg", "png", "webp", "gif").contains(ext);
            case "VIDEO" -> Set.of("mp4", "mov", "avi", "webm").contains(ext);
            case "DOCUMENT" -> Set.of("pdf").contains(ext);
            default -> false;
        };
        if (!validExt) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private static void validateTaskFileExtension(String cleanPath, String category) {
        int lastDot = cleanPath.lastIndexOf('.');
        if (lastDot == -1) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        String ext = cleanPath.substring(lastDot + 1).toLowerCase();
        boolean validExt = switch (category) {
            case "IMAGE" -> Set.of("jpg", "jpeg", "png", "webp").contains(ext);
            case "VIDEO" -> Set.of("mp4", "mov", "avi", "webm").contains(ext);
            case "DOCUMENT" -> Set.of("pdf", "doc", "docx", "xls", "xlsx", "txt").contains(ext);
            default -> false;
        };
        if (!validExt) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    public static void validateTaskFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        // Check for Path Traversal
        String cleanPath = org.springframework.util.StringUtils.cleanPath(originalFilename);
        if (cleanPath.contains("..") || cleanPath.startsWith("/") || cleanPath.contains(":\\")) {
            throw new AppException(ErrorCode.PATH_TRAVERSAL_DETECTED);
        }

        String contentType = file.getContentType();
        String category = detectTaskFileCategory(contentType);

        // Validate File Extension against Category
        validateTaskFileExtension(cleanPath, category);

        long maxSize = "DOCUMENT".equals(category) ? MAX_DOCUMENT_SIZE : ("IMAGE".equals(category) ? MAX_IMAGE_SIZE : MAX_VIDEO_SIZE);
        if (file.getSize() > maxSize) {
            String message = switch (category) {
                case "IMAGE" -> "Image size must be less than 15MB";
                case "DOCUMENT" -> "Document size must be less than 10MB";
                default -> "Video size must be less than 200MB";
            };
            throw new AppException(ErrorCode.FILE_SIZE_EXCEEDED, message);
        }

        if ("IMAGE".equals(category)) {
            validateImageMagicBytes(file, contentType);
        }
    }

    private static void validateImageMagicBytes(MultipartFile file, String contentType) {
        try (var is = file.getInputStream()) {
            byte[] header = is.readNBytes(12);
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
                case "image/gif" -> header.length >= 6
                        && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38
                        && (header[4] == 0x37 || header[4] == 0x39) && header[5] == 0x61;
                default -> false;
            };
            if (!valid) {
                throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
            }
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to read file content");
        }
    }

    private static void validatePdfMagicBytes(MultipartFile file) {
        try (var is = file.getInputStream()) {
            byte[] header = is.readNBytes(5);
            boolean valid = header.length >= 5
                    && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44
                    && header[3] == 0x46 && header[4] == 0x2D;
            if (!valid) {
                throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
            }
        } catch (IOException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to read file content");
        }
    }
}
