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
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.mapper.CampaignMapper;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.DonationMethod;
import com.mgmtp.gives.repository.UserPayOSConnectionRepository;
import com.mgmtp.gives.notification.publisher.CampaignNotificationPublisher;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.repository.CampaignFollowerRepository;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.CampaignMemberService;
import com.mgmtp.gives.service.CampaignService;
import com.mgmtp.gives.util.HtmlSanitizerUtil;
import com.mgmtp.gives.service.NotificationService;
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
    private final CampaignFollowerRepository campaignFollowerRepository;
    private final NotificationService notificationService;
    private final CampaignMemberRepository campaignMemberRepository;
    private final CampaignMemberService campaignMemberService;
    private final DonationRepository donationRepository;
    private final UserPayOSConnectionRepository userPayOSConnectionRepository;
    private final CampaignNotificationPublisher campaignNotificationPublisher;

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
            validatePendingCampaign(null, request, money, goods, currentUser);
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
                .donationMethod(request.donationMethod() != null ? request.donationMethod() : DonationMethod.MANUAL_QR)
                .bankName(request.bankName())
                .bankCode(request.bankCode())
                .bankBin(request.bankBin())
                .bankAccountNumber(request.bankAccountNumber())
                .bankAccountHolderName(request.bankAccountHolderName())
                .status(status)
                .user(currentUser)
                .categories(categories)
                .build();

        Campaign saved = campaignRepository.save(campaign);
        assert currentUser != null;
        log.info("Campaign created: id={}, title={}, userId={}", saved.getId(), saved.getTitle(), currentUser.getId());
        notificationService.broadcastDashboardUpdate();
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Campaign> getAllCampaigns(CampaignStatus status, CampaignPriority priority, List<Long> categoryIds,
            Long userId, String keyword, Boolean isFollowing, User currentUser, Pageable pageable) {
        log.info("Fetching campaigns: status={}, priority={}, categoryIds={}, userId={}, keyword={}, isFollowing={}",
                status, priority, categoryIds, userId, keyword, isFollowing);

        Specification<Campaign> followSpec = null;
        if (Boolean.TRUE.equals(isFollowing)) {
            followSpec = isFollowedBy(currentUser);
        }

        Specification<Campaign> spec = Specification.allOf(
                hasStatus(status),
                hasPriority(priority),
                hasUserId(userId),
                hasCategories(categoryIds),
                matchesKeyword(keyword),
                isVisibleTo(currentUser),
                followSpec);

        return campaignRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignById(Long id, User currentUser) {
        Campaign campaign = getCampaignByIdInternal(id);

        boolean isVisibleStatus = campaign.getStatus() == CampaignStatus.APPROVED
                || campaign.getStatus() == CampaignStatus.IN_PROGRESS
                || campaign.getStatus() == CampaignStatus.COMPLETED;

        if (currentUser == null) {
            if (!isVisibleStatus) {
                log.warn("Campaign access denied (anonymous request): campaignId={}, status={}", id,
                        campaign.getStatus());
                throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS,
                        "You do not have permission to access campaign with ID: " + id);
            }
            log.info("Public campaign retrieved successfully: id={}", id);
            return campaign;
        }

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isCreator && !isVisibleStatus
                && !campaignFollowerRepository.existsByCampaignIdAndUserId(id, currentUser.getId())) {
            log.warn("Campaign access denied (forbidden): campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_ACCESS,
                    "You do not have permission to access campaign with ID: " + id);
        }

        log.info("Campaign retrieved successfully: id={}, userId={}", id,
                currentUser.getId());
        return campaign;
    }

    @Override
    @Transactional
    public Campaign updateCampaign(Long id, CampaignRequest request, User currentUser) {
        log.info("Updating campaign: id={}, userId={}", id, currentUser.getId());
        Campaign campaign = getCampaignById(id, currentUser);

        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());
        boolean isCampaignAdmin = campaignMemberService.canManageCampaign(id, currentUser);

        if (!isCreator && !isCampaignAdmin) {
            log.warn("Update campaign denied: not creator/campaign_admin. campaignId={}, userId={}", id,
                    currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE);
        }

        if (campaign.getStatus() != CampaignStatus.DRAFT
                && campaign.getStatus() != CampaignStatus.REJECTED
                && campaign.getStatus() != CampaignStatus.PENDING) {
            log.warn("Update campaign denied: invalid status. campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE);
        }

        CampaignStatus newStatus = request.status();
        if (newStatus == null) {
            if (campaign.getStatus() == CampaignStatus.REJECTED) {
                newStatus = CampaignStatus.PENDING;
                log.info(
                        "No status requested for rejected campaign update; auto-transitioning back to PENDING. campaignId={}",
                        id);
            } else {
                newStatus = campaign.getStatus();
            }
        }

        if (newStatus != CampaignStatus.DRAFT && newStatus != CampaignStatus.PENDING) {
            log.warn(
                    "Update campaign validation failed: status {} not allowed. campaignId={}, userId={}",
                    newStatus, id, currentUser.getId());
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Campaign can only be set to DRAFT or PENDING status.");
        }

        if (newStatus == CampaignStatus.PENDING) {
            boolean money = request.acceptsMoney() != null ? request.acceptsMoney() : campaign.isAcceptsMoney();
            boolean goods = request.acceptsGoods() != null ? request.acceptsGoods() : campaign.isAcceptsGoods();
            validatePendingCampaign(campaign.getId(), request, money, goods, campaign.getUser());
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
        campaign.setDonationMethod(
                request.donationMethod() != null ? request.donationMethod() : campaign.getDonationMethod());
        campaign.setBankName(request.bankName());
        campaign.setBankCode(request.bankCode());
        campaign.setBankBin(request.bankBin());
        campaign.setBankAccountNumber(request.bankAccountNumber());
        campaign.setBankAccountHolderName(request.bankAccountHolderName());

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign updated: id={}, status={}, userId={}", saved.getId(), saved.getStatus(),
                currentUser.getId());
        notificationService.broadcastDashboardUpdate();
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
                log.warn("System delete campaign denied: invalid status. campaignId={}, status={}", id,
                        campaign.getStatus());
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
        notificationService.broadcastDashboardUpdate();
    }

    @Override
    @Transactional
    public Campaign endCampaign(Long id, User currentUser) {
        Campaign campaign = getCampaignByIdInternal(id);

        if (!campaignMemberService.canManageCampaign(id, currentUser)) {
            log.warn("End campaign denied: not campaign admin. campaignId={}, userId={}", id, currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE);
        }

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            log.warn("End campaign denied: invalid status. campaignId={}, status={}", id, campaign.getStatus());
            throw new AppException(ErrorCode.CAMPAIGN_NOT_IN_PROGRESS);
        }

        campaign.setStatus(CampaignStatus.COMPLETED);
        campaign.setEndDate(LocalDateTime.now());
        Campaign saved = campaignRepository.save(campaign);

        campaignNotificationPublisher.publishCampaignStatusChanged(
                saved, CampaignStatus.IN_PROGRESS, CampaignStatus.COMPLETED);
        notificationService.broadcastDashboardUpdate();
        log.info("Campaign ended manually: id={}, userId={}", id, currentUser.getId());
        return saved;
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

    @Override
    @Transactional
    public void completeEndedCampaignsScheduled() {
        log.info("Scanning for IN_PROGRESS campaigns past their end date");
        List<Campaign> campaignsToComplete = campaignRepository.findByStatusAndEndDateBefore(
                CampaignStatus.IN_PROGRESS, LocalDateTime.now());
        log.info("Found {} campaigns to complete", campaignsToComplete.size());
        for (Campaign campaign : campaignsToComplete) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            log.info("Campaign auto-completed: id={}, title='{}'", campaign.getId(), campaign.getTitle());
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

    private void validatePendingCampaign(Long campaignId, CampaignRequest request, boolean money, boolean goods, User owner) {
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
        if (money && request.target() < 500000) {
            log.warn("Pending campaign validation failed: target amount < 500000. target={}", request.target());
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Target amount must be at least 500,000");
        }
        if (money) {
            DonationMethod method = request.donationMethod() != null ? request.donationMethod() : DonationMethod.MANUAL_QR;
            if (method == DonationMethod.PAYOS || method == DonationMethod.HYBRID) {
                if (owner != null) {
                    boolean hasPayOS = userPayOSConnectionRepository.findByUserId(owner.getId()).isPresent();
                    if (!hasPayOS) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "You must connect your PayOS account before submitting a campaign with PayOS or Hybrid payment methods.");
                    }
                }
            }
            if (method == DonationMethod.MANUAL_QR || method == DonationMethod.HYBRID) {
                if (request.bankName() == null || request.bankName().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Bank name is required for Manual QR or Hybrid donation methods.");
                }
                if (request.bankCode() == null || request.bankCode().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Bank code is required for Manual QR or Hybrid donation methods.");
                }
                if (request.bankBin() == null || request.bankBin().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Bank BIN is required for Manual QR or Hybrid donation methods.");
                }
                if (request.bankAccountNumber() == null || request.bankAccountNumber().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Bank account number is required for Manual QR or Hybrid donation methods.");
                }
                if (request.bankAccountHolderName() == null || request.bankAccountHolderName().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Bank account holder name is required for Manual QR or Hybrid donation methods.");
                }
            }
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
            if (category.getDeletedAt() != null) {
                log.warn("Category not available for campaign: categoryId={}, deletedAt={}",
                        category.getId(), category.getDeletedAt());
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
        return campaignMediaRepository.findByCampaignIdAndContextNotAndDeletedAtIsNull(campaignId,
                MediaContext.FINAL_REPORT);
    }

    @Override
    public CampaignResponse toResponse(Campaign campaign, User currentUser) {
        if (campaign == null) {
            return null;
        }
        // 1. isEditable
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        CampaignResponse response = campaignMapper.toResponse(campaign, currentUserId, isAdmin);

        boolean isCreator = campaign.getUser() != null && currentUser != null
                && campaign.getUser().getId().equals(currentUser.getId());
        boolean isEditable = isCreator && campaign.getStatus().isEditable();
        response.setIsEditable(isEditable);

        // 2. Fetch active media, excluding FINAL_REPORT context - the final report has
        // its own
        // curated media list (see CampaignResultServiceImpl) and isn't meant to echo
        // the general
        // gallery. Announcement/meeting media stays visible here.
        List<CampaignMedia> activeMedia = campaignMediaRepository
                .findByCampaignIdAndContextNotAndDeletedAtIsNull(campaign.getId(), MediaContext.FINAL_REPORT);
        List<CampaignMediaResponse> mediaResponses = activeMedia.stream()
                .map(m -> CampaignMediaResponse.builder()
                        .id(m.getId())
                        .url(m.getUrl())
                        .mediaType(m.getMediaType())
                        .isCover(m.isCover())
                        .caption(m.getCaption())
                        .displayOrder(m.getDisplayOrder())
                        .context(m.getContext().name())
                        .build())
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

        // 4. Counts and user actions
        response.setVolunteersCount(getVolunteersCount(campaign.getId()));
        response.setDonorsCount(getDonorsCount(campaign.getId()));
        if (currentUser != null) {
            response.setIsJoined(isJoined(campaign.getId(), currentUser.getId()));
            response.setIsFollowed(isFollowed(campaign.getId(), currentUser.getId()));
            response.setHasPendingUnjoinRequest(hasPendingUnjoinRequest(campaign.getId(), currentUser.getId()));
        } else {
            response.setIsJoined(false);
            response.setIsFollowed(false);
            response.setHasPendingUnjoinRequest(false);
        }

        // 5. Donation Config fields
        response.setDonationMethod(campaign.getDonationMethod());
        response.setBankName(campaign.getBankName());
        response.setBankCode(campaign.getBankCode());
        response.setBankBin(campaign.getBankBin());
        response.setBankAccountNumber(campaign.getBankAccountNumber());
        response.setBankAccountHolderName(campaign.getBankAccountHolderName());

        boolean creatorHasPayOS = campaign.getUser() != null && userPayOSConnectionRepository
                .existsByUserId(campaign.getUser().getId());
        response.setCreatorHasPayOS(creatorHasPayOS);

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

        // 1b. Batch fetch creator PayOS statuses
        List<Long> creatorIds = campaigns.stream()
                .map(Campaign::getUser)
                .filter(java.util.Objects::nonNull)
                .map(User::getId)
                .distinct()
                .toList();
        java.util.Set<Long> creatorsWithPayOS = creatorIds.isEmpty() ? java.util.Collections.emptySet()
                : userPayOSConnectionRepository.findByUserIdIn(creatorIds).stream()
                        .map(conn -> conn.getUser().getId())
                        .collect(Collectors.toSet());

        // 2. Map and enrich each campaign
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        Long currentUserId = currentUser != null ? currentUser.getId() : null;

        return campaigns.stream().map(campaign -> {
            CampaignResponse response = campaignMapper.toResponse(campaign, currentUserId, isAdmin);

            boolean isCreator = campaign.getUser() != null && currentUser != null
                    && campaign.getUser().getId().equals(currentUser.getId());
            boolean isEditable = isCreator && campaign.getStatus().isEditable();
            response.setIsEditable(isEditable);

            response.setCoverImageUrl(coverImageMap.get(campaign.getId()));

            response.setVolunteersCount(getVolunteersCount(campaign.getId()));
            response.setDonorsCount(getDonorsCount(campaign.getId()));
            if (currentUser != null) {
                response.setIsJoined(isJoined(campaign.getId(), currentUser.getId()));
                response.setIsFollowed(isFollowed(campaign.getId(), currentUser.getId()));
                response.setHasPendingUnjoinRequest(hasPendingUnjoinRequest(campaign.getId(), currentUser.getId()));
            } else {
                response.setIsJoined(false);
                response.setIsFollowed(false);
                response.setHasPendingUnjoinRequest(false);
            }

            response.setDonationMethod(campaign.getDonationMethod());
            response.setBankName(campaign.getBankName());
            response.setBankCode(campaign.getBankCode());
            response.setBankBin(campaign.getBankBin());
            response.setBankAccountNumber(campaign.getBankAccountNumber());
            response.setBankAccountHolderName(campaign.getBankAccountHolderName());
            boolean hasPayOS = campaign.getUser() != null && creatorsWithPayOS.contains(campaign.getUser().getId());
            response.setCreatorHasPayOS(hasPayOS);

            return response;
        }).toList();
    }

    @Transactional(readOnly = true)
    public boolean isFollowed(Long campaignId, Long userId) {
        return campaignFollowerRepository.existsByCampaignIdAndUserId(campaignId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isJoined(Long campaignId, Long userId) {
        return campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(campaignId, userId,
                CampaignMemberRole.VOLUNTEER);
    }

    @Transactional(readOnly = true)
    public boolean hasPendingUnjoinRequest(Long campaignId, Long userId) {
        return campaignMemberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .map(cm -> cm.getUnjoinRequestedAt() != null)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public long getVolunteersCount(Long campaignId) {
        return campaignMemberRepository.countByCampaignIdAndRoleInCampaign(campaignId, CampaignMemberRole.VOLUNTEER);
    }

    @Override
    @Transactional(readOnly = true)
    public long getDonorsCount(Long campaignId) {
        return donationRepository.countDistinctDonorsByCampaignIdAndStatusSuccessful(campaignId);
    }
}
