package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.dto.campaign.CampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.CategoryStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.service.CampaignService;
import com.mgmtp.gives.util.HtmlSanitizerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mgmtp.gives.specification.CampaignSpecifications.*;

import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final CategoryRepository categoryRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignMapper campaignMapper;

    @Value("${app.media.upload-dir}")
    private String uploadDir;

    @Override
    @Transactional
    public Campaign createCampaign(CampaignRequest request, User currentUser) {
        log.info("Creating campaign: title={}, userId={}", request.title(),
                currentUser != null ? currentUser.getId() : null);
        validateDateRange(request);

        CampaignStatus status = request.status();
        if (status == null) {
            status = CampaignStatus.DRAFT;
        }

        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        if (!isAdmin && status != CampaignStatus.DRAFT && status != CampaignStatus.PENDING) {
            log.warn("Campaign creation failed: status not allowed for user. status={}, userId={}",
                    status, currentUser != null ? currentUser.getId() : null);
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Regular users can only create campaigns in DRAFT or PENDING status.");
        }

        if (status == CampaignStatus.PENDING) {
            boolean money = request.acceptsMoney() != null ? request.acceptsMoney() : true;
            boolean goods = request.acceptsGoods() != null ? request.acceptsGoods() : true;
            validatePendingCampaign(null, request, money, goods);
        }

        Set<Category> categories = fetchAndValidateCategories(request.categories());

        Campaign campaign = Campaign.builder()
                .title(request.title())
                .description(HtmlSanitizerUtil.sanitize(request.description()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .target(request.target())
                .priority(request.priority() != null ? request.priority() : CampaignPriority.NORMAL)
                .acceptsMoney(request.acceptsMoney() != null ? request.acceptsMoney() : true)
                .acceptsGoods(request.acceptsGoods() != null ? request.acceptsGoods() : true)
                .status(status)
                .user(currentUser)
                .categories(categories)
                .build();

        Campaign saved = campaignRepository.save(campaign);
        assert currentUser != null;
        log.info("Campaign created: id={}, title={}, userId={}", saved.getId(), saved.getTitle(), currentUser.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Campaign> getAllCampaigns(CampaignStatus status, CampaignPriority priority, Long categoryId,
            Long userId, String keyword, User currentUser, Pageable pageable) {
        log.info("Fetching campaigns: status={}, priority={}, categoryId={}, userId={}, keyword={}",
                status, priority, categoryId, userId, keyword);
        Specification<Campaign> spec = Specification.allOf(
                hasStatus(status),
                hasPriority(priority),
                hasUserId(userId),
                hasCategory(categoryId),
                matchesKeyword(keyword),
                isVisibleTo(currentUser));

        return campaignRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignById(Long id, User currentUser) {
        if (currentUser == null) {
            log.warn("Campaign access denied (unauthenticated request): campaignId={}", id);
            throw new AppException(ErrorCode.UNAUTHORIZED, "User must be authenticated to view campaign details");
        }

        Campaign campaign = getCampaignByIdInternal(id);

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());
        boolean isVisibleStatus = campaign.getStatus() == CampaignStatus.APPROVED
                || campaign.getStatus() == CampaignStatus.IN_PROGRESS
                || campaign.getStatus() == CampaignStatus.COMPLETED;

        if (!isAdmin && !isCreator && !isVisibleStatus) {
            log.warn("Campaign access denied (forbidden): campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS,
                    "You do not have permission to access campaign with ID: " + id);
        }

        log.info("Campaign retrieved successfully: id={}, userId={}", id,
                currentUser != null ? currentUser.getId() : null);
        return campaign;
    }

    @Override
    @Transactional
    public Campaign updateCampaign(Long id, CampaignRequest request, User currentUser) {
        log.info("Updating campaign: id={}, userId={}", id, currentUser.getId());
        Campaign campaign = getCampaignById(id, currentUser);

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isCreator) {
            log.warn("Update campaign denied: not admin/creator. campaignId={}, userId={}", id, currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE);
        }

        if (!isAdmin && campaign.getStatus() != CampaignStatus.DRAFT
                && campaign.getStatus() != CampaignStatus.REJECTED
                && campaign.getStatus() != CampaignStatus.PENDING) {
            log.warn("Update campaign denied: invalid status for non-admin. campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE);
        }

        CampaignStatus newStatus = request.status();
        if (newStatus == null) {
            if (!isAdmin && campaign.getStatus() == CampaignStatus.REJECTED) {
                newStatus = CampaignStatus.PENDING;
                log.info(
                        "No status requested for rejected campaign update; auto-transitioning back to PENDING. campaignId={}",
                        id);
            } else {
                newStatus = campaign.getStatus();
            }
        }

        if (!isAdmin && newStatus != CampaignStatus.DRAFT && newStatus != CampaignStatus.PENDING) {
            log.warn(
                    "Update campaign validation failed: status {} not allowed for regular user. campaignId={}, userId={}",
                    newStatus, id, currentUser.getId());
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Regular users can only set status to DRAFT or PENDING.");
        }

        if (newStatus == CampaignStatus.PENDING) {
            boolean money = request.acceptsMoney() != null ? request.acceptsMoney() : campaign.isAcceptsMoney();
            boolean goods = request.acceptsGoods() != null ? request.acceptsGoods() : campaign.isAcceptsGoods();
            validatePendingCampaign(campaign.getId(), request, money, goods);
        }

        validateDateRange(request);
        Set<Category> categories = fetchAndValidateCategories(request.categories());

        campaign.setTitle(request.title());
        campaign.setDescription(HtmlSanitizerUtil.sanitize(request.description()));
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setTarget(request.target());
        campaign.setPriority(request.priority() != null ? request.priority() : campaign.getPriority());
        campaign.setAcceptsMoney(request.acceptsMoney() != null ? request.acceptsMoney() : campaign.isAcceptsMoney());
        campaign.setAcceptsGoods(request.acceptsGoods() != null ? request.acceptsGoods() : campaign.isAcceptsGoods());
        campaign.setCategories(categories);
        campaign.setStatus(newStatus);

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign updated: id={}, status={}, userId={}", saved.getId(), saved.getStatus(),
                currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id, User currentUser) {
        Campaign campaign = getCampaignByIdInternal(id);

        if (currentUser != null) {
            boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());
            if (!isCreator) {
                log.warn("Delete campaign denied: not creator. campaignId={}, userId={}", id, currentUser.getId());
                throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_DELETE);
            }
            if (campaign.getStatus() != CampaignStatus.PENDING 
                    && campaign.getStatus() != CampaignStatus.REJECTED 
                    && campaign.getStatus() != CampaignStatus.DRAFT) {
                log.warn("Delete campaign denied: invalid status. campaignId={}, status={}", id, campaign.getStatus());
                throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_DELETE);
            }
        } else {
            log.info("System initiated deletion for campaign: id={}", id);
            if (campaign.getStatus() != CampaignStatus.REJECTED) {
                log.warn("System delete campaign denied: invalid status. campaignId={}, status={}", id, campaign.getStatus());
                throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_DELETE);
            }
        }

        // Delete physical files on disk
        if (campaign.getMedias() != null) {
            for (CampaignMedia media : campaign.getMedias()) {
                if (media.getUrl() != null) {
                    Path path = Paths.get(uploadDir).resolve(media.getUrl());
                    try {
                        Files.deleteIfExists(path);
                        log.info("Deleted physical media file: {}", media.getUrl());
                    } catch (IOException e) {
                        log.warn("Failed to delete physical file: {}", media.getUrl(), e);
                    }
                }
            }
        }

        campaignRepository.delete(campaign);
        log.info("Campaign deleted successfully: id={}, title={}", id, campaign.getTitle());
    }

    @Override
    @Transactional
    public void startApprovedCampaignsScheduled() {
        java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime endOfDay = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);

        log.info("Scanning for APPROVED campaigns starting today: {} to {}", startOfDay, endOfDay);

        List<Campaign> campaignsToStart = campaignRepository.findByStatusAndStartDateBetween(
                CampaignStatus.APPROVED, startOfDay, endOfDay);

        log.info("Found {} campaigns to start", campaignsToStart.size());

        for (Campaign campaign : campaignsToStart) {
            campaign.setStatus(CampaignStatus.IN_PROGRESS);
            campaignRepository.save(campaign);
            log.info("Campaign activated to IN_PROGRESS: id={}, title='{}'", campaign.getId(), campaign.getTitle());
        }
    }

    private void validateDateRange(CampaignRequest request) {
        LocalDateTime limit = LocalDateTime.now().toLocalDate().atStartOfDay();
        if (request.startDate() != null && request.startDate().isBefore(limit)) {
            log.warn("Campaign date validation failed: startDate={}, limit={}",
                    request.startDate(), limit);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Start date cannot be in the past");
        }
        if (request.startDate() != null && request.endDate() != null) {
            if (!request.startDate().isBefore(request.endDate())) {
                log.warn("Campaign date validation failed: startDate={}, endDate={}",
                        request.startDate(), request.endDate());
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Start date must be before end date");
            }
        }
    }

    private void validatePendingCampaign(Long campaignId, CampaignRequest request, boolean money, boolean goods) {
        if (request.description() == null || request.description().trim().isEmpty()) {
            log.warn("Pending campaign validation failed: description is empty");
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Description is required for submission");
        }
        if (request.categories() == null || request.categories().isEmpty()) {
            log.warn("Pending campaign validation failed: no categories");
            throw new AppException(ErrorCode.VALIDATION_ERROR, "At least one category is required for submission");
        }
        if (!money && !goods) {
            log.warn("Pending campaign validation failed: accepts neither money nor goods");
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Campaign must accept money, goods, or both");
        }
        if (money && request.target() == null) {
            log.warn("Pending campaign validation failed: target is null");
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Target amount is required for submission");
        }
        if (money && request.target() <= 0) {
            log.warn("Pending campaign validation failed: target amount <= 0. target={}", request.target());
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Target amount must be positive");
        }
        if (request.startDate() == null) {
            log.warn("Pending campaign validation failed: start date is null");
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Start date is required for submission");
        }
        if (request.endDate() == null) {
            log.warn("Pending campaign validation failed: end date is null");
            throw new AppException(ErrorCode.VALIDATION_ERROR, "End date is required for submission");
        }
        if (campaignId != null) {
            boolean hasCover = campaignMediaRepository.existsByCampaignIdAndDeletedAtIsNullAndIsCoverTrue(campaignId);
            if (!hasCover) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "At least one cover image is required for submission");
            }
        }
    }

    private Set<Category> fetchAndValidateCategories(Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Category> categoryList = categoryRepository.findAllById(categoryIds);
        if (categoryList.size() != categoryIds.size()) {
            log.warn("Invalid category IDs in campaign request: requested={}, found={}",
                    categoryIds, categoryList.size());
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND, "One or more category IDs are invalid");
        }
        for (Category category : categoryList) {
            if (category.getStatus() == CategoryStatus.REJECTED || category.getStatus() == CategoryStatus.HIDDEN) {
                log.warn("Category not available for campaign: categoryId={}, status={}",
                        category.getId(), category.getStatus());
                throw new AppException(ErrorCode.CATEGORY_NOT_AVAILABLE,
                        "Category '" + category.getName() + "' is not available");
            }
        }
        return new HashSet<>(categoryList);
    }

    private Campaign getCampaignByIdInternal(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Campaign not found: id={}", id);
                    return new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                            "Campaign not found with ID: " + id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignMedia> getActiveMediasByCampaignId(Long campaignId) {
        return campaignMediaRepository.findByCampaignIdAndDeletedAtIsNull(campaignId);
    }

    @Override
    public CampaignResponse toResponse(Campaign campaign, User currentUser) {
        if (campaign == null) {
            return null;
        }
        CampaignResponse response = campaignMapper.toResponse(campaign);

        // 1. isEditable
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && currentUser != null
                && campaign.getUser().getId().equals(currentUser.getId());
        boolean isEditable = isAdmin || (isCreator && campaign.getStatus().isEditable());
        response.setIsEditable(isEditable);

        // 2. Fetch active media
        List<CampaignMedia> activeMedia = campaignMediaRepository.findByCampaignIdAndDeletedAtIsNull(campaign.getId());
        List<CampaignMediaResponse> mediaResponses = activeMedia.stream()
                .map(m -> new CampaignMediaResponse(m.getId(), m.getUrl(), m.getMediaType(), m.isCover()))
                .toList();
        response.setMedia(mediaResponses);
        response.setMedias(mediaResponses);

        // 3. coverImageUrl
        String coverImageUrl = activeMedia.stream()
                .filter(CampaignMedia::isCover)
                .map(CampaignMedia::getUrl)
                .findFirst()
                .orElse(null);
        response.setCoverImageUrl(coverImageUrl);

        return response;
    }

    @Override
    public List<CampaignResponse> toResponseList(List<Campaign> campaigns, User currentUser) {
        if (campaigns == null || campaigns.isEmpty()) {
            return List.of();
        }

        // 1. Batch fetch cover images
        List<Long> campaignIds = campaigns.stream().map(Campaign::getId).toList();
        List<CampaignMedia> coverImages = campaignMediaRepository.findCoverImagesByCampaignIds(campaignIds);
        java.util.Map<Long, String> coverImageMap = coverImages.stream()
                .collect(Collectors.toMap(
                        m -> m.getCampaign().getId(),
                        CampaignMedia::getUrl,
                        (existing, replacement) -> existing));

        // 2. Map and enrich each campaign
        return campaigns.stream().map(campaign -> {
            CampaignResponse response = campaignMapper.toResponse(campaign);

            boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
            boolean isCreator = campaign.getUser() != null && currentUser != null
                    && campaign.getUser().getId().equals(currentUser.getId());
            boolean isEditable = isAdmin || (isCreator && campaign.getStatus().isEditable());
            response.setIsEditable(isEditable);

            response.setCoverImageUrl(coverImageMap.get(campaign.getId()));
            return response;
        }).toList();
    }
}
