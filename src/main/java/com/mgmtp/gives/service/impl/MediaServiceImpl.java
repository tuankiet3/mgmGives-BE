package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.repository.CampaignMediaRepository;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;

    @Value("${app.media.upload-dir}")
    private String uploadDir;

    @Override
    @Transactional
    public CampaignMedia uploadCampaignMedia(MultipartFile file, Long campaignId) {
        MediaValidationUtil.validateFile(file, false);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + campaignId));

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                : "";
        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path targetPath = Paths.get(uploadDir).resolve(filename);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to store campaign media file: path={}", targetPath, e);
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to store file");
        }

        CampaignMedia media = CampaignMedia.builder()
                .url(filename)
                .mediaType(MediaValidationUtil.detectCategory(file.getContentType()))
                .campaign(campaign)
                .build();

        CampaignMedia saved = campaignMediaRepository.save(media);
        log.info("Campaign media uploaded: id={}, file={}, type={}, campaignId={}", saved.getId(), filename, saved.getMediaType(), campaignId);
        return saved;
    }

    @Override
    @Transactional
    public CampaignMedia softDeleteCampaignMedia(Long id) {
        CampaignMedia media = campaignMediaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_MEDIA_NOT_FOUND,
                        "Campaign media not found with ID: " + id));

        if (media.getDeletedAt() != null) {
            throw new AppException(ErrorCode.MEDIA_ALREADY_DELETED);
        }

        media.setDeletedAt(LocalDateTime.now());
        CampaignMedia saved = campaignMediaRepository.save(media);
        log.info("Campaign media soft deleted: id={}, file={}", saved.getId(), saved.getUrl());
        return saved;
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
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
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
    @Transactional(readOnly = true)
    public void assertFileAccessible(String filename) {
        campaignMediaRepository.findByUrl(filename).ifPresent(media -> {
            if (media.getDeletedAt() != null) {
                log.warn("Access attempt on soft-deleted media: file={}", filename);
                throw new ResourceNotFoundException(ErrorCode.MEDIA_NOT_FOUND,
                        "Media file not found or has been deleted");
            }
        });
    }
}
