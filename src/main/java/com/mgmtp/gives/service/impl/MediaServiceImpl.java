package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignSpending;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.UserRepository;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.util.MediaValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;

    @Value("${app.media.upload-dir}")
    private String uploadDir;

    // Contexts a caller may self-assign at upload time. Announcement/meeting media
    // doesn't need
    // its own context - it's scoped by FK (announcement_id / meeting_id) and stays
    // visible in
    // the general gallery like any other campaign media, so it's uploaded as the
    // default
    // CAMPAIGN context.
    private static final Set<MediaContext> SELF_ASSIGNABLE_CONTEXTS = Set.of(MediaContext.CAMPAIGN,
            MediaContext.FINAL_REPORT);

    @Override
    @Transactional
    public CampaignMediaResponse uploadCampaignMedia(
            MultipartFile file, Long campaignId, boolean isCover, String context, User currentUser) {
        MediaValidationUtil.validateFile(file, false);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + campaignId));

        if (!canManageCampaignMedia(campaign, currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only the campaign creator or a campaign admin can upload media");
        }

        String detectedType = MediaValidationUtil.detectCategory(file.getContentType());
        if (isCover && !"IMAGE".equals(detectedType)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only image files can be set as cover image");
        }

        MediaContext resolvedContext = resolveSelfAssignedContext(context);
        if (isCover && resolvedContext == MediaContext.FINAL_REPORT) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Final report media cannot be set as a cover image");
        }

        String filename = storeFile(file, "campaign media");

        CampaignMedia media = CampaignMedia.builder()
                .url(filename)
                .mediaType(detectedType)
                .isCover(isCover)
                .campaign(campaign)
                .context(resolvedContext)
                .build();

        CampaignMedia saved = campaignMediaRepository.save(media);

        // If isCover is true, soft-delete any existing cover image within the SAME
        // context -
        // a cover image only makes sense as "the" cover for the gallery it belongs to,
        // so this
        // must not reach into (or clear) another feature's media.
        if (isCover) {
            campaignMediaRepository.findByCampaignIdAndContextAndDeletedAtIsNull(campaignId, resolvedContext)
                    .stream()
                    .filter(CampaignMedia::isCover)
                    .filter(m -> !m.getId().equals(saved.getId()))
                    .forEach(this::softDeleteMedia);
        }

        log.info("Campaign media uploaded: id={}, file={}, type={}, isCover={}, campaignId={}",
                saved.getId(), filename, saved.getMediaType(), saved.isCover(), campaignId);
        return CampaignMediaResponse.builder()
                .id(saved.getId()).url(saved.getUrl()).mediaType(saved.getMediaType()).isCover(saved.isCover())
                .caption(saved.getCaption()).displayOrder(saved.getDisplayOrder()).context(saved.getContext().name())
                .build();
    }

    private static MediaContext resolveSelfAssignedContext(String context) {
        if (context == null || context.isBlank()) {
            return MediaContext.CAMPAIGN;
        }
        MediaContext parsed;
        try {
            parsed = MediaContext.valueOf(context);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid media context: " + context);
        }
        if (!SELF_ASSIGNABLE_CONTEXTS.contains(parsed)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid media context: " + context);
        }
        return parsed;
    }

    @Override
    @Transactional
    public List<CampaignMedia> reconcileMediaTags(
            Long campaignId,
            List<CampaignMedia> currentlyTagged,
            List<Long> requestedMediaIds,
            Consumer<CampaignMedia> onRevert,
            BiConsumer<CampaignMedia, Integer> onClaim) {
        if (requestedMediaIds == null) {
            return currentlyTagged;
        }

        List<Long> uniqueIds = requestedMediaIds.stream().distinct().toList();
        Map<Long, CampaignMedia> currentlyTaggedById = currentlyTagged.stream()
                .collect(Collectors.toMap(CampaignMedia::getId, m -> m));

        List<CampaignMedia> toSave = new ArrayList<>();
        for (CampaignMedia media : currentlyTagged) {
            if (!uniqueIds.contains(media.getId())) {
                onRevert.accept(media);
                toSave.add(media);
            }
        }

        List<Long> idsNeedingFetch = uniqueIds.stream()
                .filter(id -> !currentlyTaggedById.containsKey(id))
                .toList();
        Map<Long, CampaignMedia> fetchedById = idsNeedingFetch.isEmpty()
                ? Map.of()
                : campaignMediaRepository.findAllById(idsNeedingFetch).stream()
                        .collect(Collectors.toMap(CampaignMedia::getId, m -> m));

        List<CampaignMedia> claimed = new ArrayList<>();
        for (int i = 0; i < uniqueIds.size(); i++) {
            Long id = uniqueIds.get(i);
            CampaignMedia media = currentlyTaggedById.containsKey(id) ? currentlyTaggedById.get(id)
                    : fetchedById.get(id);
            if (media == null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Media with ID " + id + " does not exist");
            }
            if (media.getDeletedAt() != null) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Media with ID " + id + " has been deleted");
            }
            if (media.getCampaign() == null || !Objects.equals(media.getCampaign().getId(), campaignId)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Media with ID " + id + " does not belong to campaign " + campaignId);
            }
            onClaim.accept(media, i);
            toSave.add(media);
            claimed.add(media);
        }

        if (!toSave.isEmpty()) {
            campaignMediaRepository.saveAll(toSave);
        }
        return claimed;
    }

    @Override
    @Transactional
    public CampaignMediaResponse uploadCampaignMeetingAttachment(
            MultipartFile file,
            Campaign campaign,
            CampaignMeeting meeting) {
        MediaValidationUtil.validateFile(file, false);

        String detectedType = MediaValidationUtil.detectCategory(file.getContentType());
        String filename = storeFile(file, "campaign meeting attachment");

        CampaignMedia media = CampaignMedia.builder()
                .url(filename)
                .mediaType(detectedType)
                .isCover(false)
                .campaign(campaign)
                .meeting(meeting)
                .build();

        CampaignMedia saved = campaignMediaRepository.save(media);
        log.info("Campaign meeting attachment uploaded: id={}, file={}, type={}, campaignId={}, meetingId={}",
                saved.getId(), filename, saved.getMediaType(), campaign.getId(), meeting.getId());
        return new CampaignMediaResponse(saved.getId(), saved.getUrl(), saved.getMediaType(), saved.isCover(),
                saved.getCaption(), saved.getDisplayOrder(), saved.getContext().name());
    }

    @Override
    @Transactional
    public CampaignMediaResponse uploadCampaignSpendingAttachment(
            MultipartFile file,
            Campaign campaign,
            CampaignSpending spending) {
        MediaValidationUtil.validateFile(file, false);

        String detectedType = MediaValidationUtil.detectCategory(file.getContentType());
        String filename = storeFile(file, "campaign spending attachment");

        CampaignMedia media = CampaignMedia.builder()
                .url(filename)
                .mediaType(detectedType)
                .isCover(false)
                .campaign(campaign)
                .spending(spending)
                .build();

        CampaignMedia saved = campaignMediaRepository.save(media);
        log.info("Campaign spending attachment uploaded: id={}, file={}, type={}, campaignId={}, spendingId={}",
                saved.getId(), filename, saved.getMediaType(), campaign.getId(), spending.getId());
        return new CampaignMediaResponse(saved.getId(), saved.getUrl(), saved.getMediaType(), saved.isCover(),
                saved.getCaption(), saved.getDisplayOrder(), saved.getContext().name());
    }

    @Override
    @Transactional
    public CampaignMediaResponse softDeleteCampaignMedia(Long id, User currentUser) {
        CampaignMedia media = campaignMediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_MEDIA_NOT_FOUND,
                        "Campaign media not found with ID: " + id));

        if (!canManageCampaignMedia(media.getCampaign(), currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only the campaign creator or a campaign admin can delete media");
        }

        if (media.isCover()) {
            CampaignStatus campaignStatus = media.getCampaign().getStatus();
            if (campaignStatus != CampaignStatus.DRAFT
                    && campaignStatus != CampaignStatus.PENDING
                    && campaignStatus != CampaignStatus.REJECTED) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Cannot remove cover photo directly");
            }
        }

        if (media.getDeletedAt() != null) {
            throw new AppException(ErrorCode.MEDIA_ALREADY_DELETED);
        }

        softDeleteMedia(media);
        return CampaignMediaResponse.builder()
                .id(media.getId()).url(media.getUrl()).mediaType(media.getMediaType()).isCover(media.isCover())
                .caption(media.getCaption()).displayOrder(media.getDisplayOrder()).context(media.getContext().name())
                .build();
    }

    @Override
    @Transactional
    public CampaignMediaResponse softDeleteCampaignMeetingAttachment(CampaignMedia media) {
        if (media.getDeletedAt() != null) {
            throw new AppException(ErrorCode.MEDIA_ALREADY_DELETED);
        }
        softDeleteMedia(media);
        return new CampaignMediaResponse(media.getId(), media.getUrl(), media.getMediaType(), media.isCover(),
                media.getCaption(), media.getDisplayOrder(), media.getContext().name());
    }

    @Override
    @Transactional
    public CampaignMediaResponse softDeleteCampaignSpendingAttachment(CampaignMedia media) {
        if (media.getDeletedAt() != null) {
            throw new AppException(ErrorCode.MEDIA_ALREADY_DELETED);
        }
        softDeleteMedia(media);
        return new CampaignMediaResponse(media.getId(), media.getUrl(), media.getMediaType(), media.isCover(),
                media.getCaption(), media.getDisplayOrder(), media.getContext().name());
    }

    private boolean canManageCampaignMedia(Campaign campaign, User user) {
        if (campaign.getUser() != null && campaign.getUser().getId().equals(user.getId()))
            return true;
        return campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaign.getId(), user.getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
    }

    private void softDeleteMedia(CampaignMedia media) {
        if (media.getDeletedAt() != null) {
            return;
        }

        // Move file to /app/media/trash
        if (media.getUrl() != null && !media.getUrl().isBlank()) {
            Path source = Paths.get(uploadDir).resolve(media.getUrl());
            Path trashDir = Paths.get(uploadDir).resolve("trash");
            Path target = trashDir.resolve(media.getUrl());
            try {
                if (Files.exists(source)) {
                    Files.createDirectories(trashDir);
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Moved campaign media to trash: id={}, file={}", media.getId(), media.getUrl());
                } else {
                    log.warn("Campaign media file not found on disk during soft delete: id={}, file={}", media.getId(),
                            media.getUrl());
                }
            } catch (IOException e) {
                log.error("Failed to move campaign media to trash: id={}, file={}", media.getId(), media.getUrl(), e);
                throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to move file to trash");
            }
        }

        media.setDeletedAt(LocalDateTime.now());
        campaignMediaRepository.save(media);
        log.info("Campaign media soft deleted: id={}, file={}", media.getId(), media.getUrl());
    }

    private String storeFile(MultipartFile file, String context) {
        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                : "";
        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path targetPath = Paths.get(uploadDir).resolve(filename);
        try (var is = file.getInputStream()) {
            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store {} file: path={}", context, targetPath, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to store file");
        }
        return filename;
    }

    @Override
    @Transactional
    public CampaignMedia restoreCampaignMedia(Long id) {
        CampaignMedia media = campaignMediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_MEDIA_NOT_FOUND,
                        "Campaign media not found with ID: " + id));

        if (media.getDeletedAt() == null) {
            throw new AppException(ErrorCode.MEDIA_NOT_DELETED);
        }

        if (LocalDateTime.now().isAfter(media.getDeletedAt().plusDays(14))) {
            throw new AppException(ErrorCode.MEDIA_RESTORE_EXPIRED);
        }

        // move file back to /app/media
        if (media.getUrl() != null && !media.getUrl().isBlank()) {
            Path source = Paths.get(uploadDir).resolve("trash").resolve(media.getUrl());
            Path targetDir = Paths.get(uploadDir);
            Path target = targetDir.resolve(media.getUrl());
            try {
                if (Files.exists(source)) {
                    Files.createDirectories(targetDir);
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    log.info("Restored campaign media from trash: id={}, file={}", media.getId(), media.getUrl());
                } else {
                    log.warn("Campaign media file not found in trash during restore: id={}, file={}", media.getId(),
                            media.getUrl());
                    throw new ResourceNotFoundException(ErrorCode.MEDIA_NOT_FOUND,
                            "Campaign media file not found in trash during restore: " + media.getUrl());
                }
            } catch (IOException e) {
                log.error("Failed to restore campaign media from trash: id={}, file={}", media.getId(), media.getUrl(),
                        e);
                throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to restore file from trash");
            }
        }

        media.setDeletedAt(null);
        CampaignMedia saved = campaignMediaRepository.save(media);
        log.info("Campaign media restored: id={}, file={}", saved.getId(), saved.getUrl());
        return saved;
    }

    @Override
    @Transactional
    public String uploadAvatar(MultipartFile file, User currentUser) {
        MediaValidationUtil.validateFile(file, true);

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                        "User not found with ID: " + currentUser.getId()));

        String oldAvatar = user.getAvatarUrl();

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                : "";
        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path targetPath = Paths.get(uploadDir).resolve(filename);
        try (var is = file.getInputStream()) {
            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store avatar file: path={}", targetPath, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to store file");
        }

        user.setAvatarUrl(filename);
        userRepository.save(user);

        if (oldAvatar != null && !oldAvatar.isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(uploadDir).resolve(oldAvatar));
            } catch (IOException e) {
                log.warn("Could not delete old avatar file: {}", oldAvatar, e);
            }
        }

        log.info("Avatar uploaded: userId={}, file={}", currentUser.getId(), filename);
        return filename;
    }

    @Override
    @Transactional
    public void deleteAvatar(User currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                        "User not found with ID: " + currentUser.getId()));

        String currentAvatar = user.getAvatarUrl();
        if (currentAvatar == null || currentAvatar.isBlank()) {
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND, "User has no avatar to delete");
        }

        Path filePath = Paths.get(uploadDir).resolve(currentAvatar);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete avatar file: path={}", filePath, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to delete avatar file");
        }

        user.setAvatarUrl(null);
        userRepository.save(user);
        log.info("Avatar deleted: userId={}, file={}", currentUser.getId(), currentAvatar);
    }

    @Override
    @Transactional
    public String uploadCampaignQr(MultipartFile file, User currentUser) {
        MediaValidationUtil.validateFile(file, true);
        String filename = storeFile(file, "campaign QR");
        log.info("Campaign QR uploaded: userId={}, file={}", currentUser.getId(), filename);
        return filename;
    }

    @Override
    public String uploadTaskFile(MultipartFile file) {
        MediaValidationUtil.validateTaskFile(file);
        String filename = storeFile(file, "task attachment");
        log.info("Task attachment stored: file={}", filename);
        return filename;
    }

    @Override
    @Transactional
    public String uploadTransactionProof(MultipartFile file) {
        MediaValidationUtil.validateFile(file, true);
        String filename = storeFile(file, "transaction proof");
        log.info("Transaction proof uploaded: file={}", filename);
        return filename;
    }

    @Override
    public void softDeleteTaskFile(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        Path source = Paths.get(uploadDir).resolve(storedFilename);
        Path trashDir = Paths.get(uploadDir).resolve("trash");
        Path target = trashDir.resolve(storedFilename);
        try {
            if (Files.exists(source)) {
                Files.createDirectories(trashDir);
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Task attachment moved to trash: file={}", storedFilename);
            } else {
                log.warn("Task attachment file not found on disk during soft delete: file={}", storedFilename);
            }
        } catch (IOException e) {
            log.error("Failed to move task attachment file to trash: file={}", storedFilename, e);
        }
    }
}
