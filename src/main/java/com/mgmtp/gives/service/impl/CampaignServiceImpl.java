package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Category;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.CategoryStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.CategoryRepository;
import com.mgmtp.gives.service.CampaignService;
import static com.mgmtp.gives.specification.CampaignSpecifications.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignServiceImpl implements CampaignService {

    private final CampaignRepository campaignRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public Campaign createCampaign(CampaignRequest request, User currentUser) {
        log.info("Creating campaign: title={}, userId={}", request.title(), currentUser != null ? currentUser.getId() : null);
        validateDateRange(request);

        CampaignStatus status = request.status();
        if (status == null) {
            status = CampaignStatus.DRAFT;
        }

        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        if (!isAdmin && status != CampaignStatus.DRAFT && status != CampaignStatus.PENDING) {
            log.warn("Campaign creation failed: status not allowed for user. status={}, userId={}",
                    status, currentUser != null ? currentUser.getId() : null);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Regular users can only create campaigns in DRAFT or PENDING status.");
        }

        if (status == CampaignStatus.PENDING) {
            boolean money = request.acceptsMoney() != null ? request.acceptsMoney() : true;
            boolean goods = request.acceptsGoods() != null ? request.acceptsGoods() : true;
            validatePendingCampaign(request, money, goods);
        }

        Set<Category> categories = fetchAndValidateCategories(request.categories());

        Campaign campaign = Campaign.builder()
                .title(request.title())
                .description(request.description())
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
                isVisibleTo(currentUser)
        );

        return campaignRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignById(Long id, User currentUser) {
        log.info("Fetching campaign: id={}, userId={}", id, currentUser != null ? currentUser.getId() : null);
        Campaign campaign = getCampaignByIdInternal(id);

        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && currentUser != null && campaign.getUser().getId().equals(currentUser.getId());
        boolean isApproved = campaign.getStatus() == CampaignStatus.APPROVED;

        if (!isAdmin && !isCreator && !isApproved) {
            log.warn("Campaign access denied (not visible to user): campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser != null ? currentUser.getId() : null);
            throw new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                    "Campaign not found with ID: " + id);
        }

        log.info("Campaign retrieved successfully: id={}, userId={}", id, currentUser != null ? currentUser.getId() : null);
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
                && campaign.getStatus() != CampaignStatus.REJECTED) {
            log.warn("Update campaign denied: invalid status for non-admin. campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE);
        }

        CampaignStatus newStatus = request.status();
        if (newStatus == null) {
            if (!isAdmin && campaign.getStatus() == CampaignStatus.REJECTED) {
                newStatus = CampaignStatus.PENDING;
                log.info("No status requested for rejected campaign update; auto-transitioning back to PENDING. campaignId={}", id);
            } else {
                newStatus = campaign.getStatus();
            }
        }

        if (!isAdmin && newStatus != CampaignStatus.DRAFT && newStatus != CampaignStatus.PENDING) {
            log.warn("Update campaign validation failed: status {} not allowed for regular user. campaignId={}, userId={}",
                    newStatus, id, currentUser.getId());
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Regular users can only set status to DRAFT or PENDING.");
        }

        if (newStatus == CampaignStatus.PENDING) {
            boolean money = request.acceptsMoney() != null ? request.acceptsMoney() : campaign.isAcceptsMoney();
            boolean goods = request.acceptsGoods() != null ? request.acceptsGoods() : campaign.isAcceptsGoods();
            validatePendingCampaign(request, money, goods);
        }

        validateDateRange(request);
        Set<Category> categories = fetchAndValidateCategories(request.categories());

        campaign.setTitle(request.title());
        campaign.setDescription(request.description());
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setTarget(request.target());
        campaign.setPriority(request.priority() != null ? request.priority() : campaign.getPriority());
        campaign.setAcceptsMoney(request.acceptsMoney() != null ? request.acceptsMoney() : campaign.isAcceptsMoney());
        campaign.setAcceptsGoods(request.acceptsGoods() != null ? request.acceptsGoods() : campaign.isAcceptsGoods());
        campaign.setCategories(categories);
        campaign.setStatus(newStatus);

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign updated: id={}, status={}, userId={}", saved.getId(), saved.getStatus(), currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id, User currentUser) {
        Campaign campaign = getCampaignByIdInternal(id);

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());

        if (!isAdmin) {
            if (!isCreator) {
                throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE);
            }
            if (campaign.getStatus() != CampaignStatus.DRAFT) {
                throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE, "Only draft campaigns can be deleted");
            }
        }

        campaignRepository.delete(campaign);
        log.info("Campaign deleted: id={}, title={}", id, campaign.getTitle());
    }

    private void validateDateRange(CampaignRequest request) {
        if (request.startDate() != null && request.endDate() != null) {
            if (!request.startDate().isBefore(request.endDate())) {
                log.warn("Campaign date validation failed: startDate={}, endDate={}",
                        request.startDate(), request.endDate());
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Start date must be before end date");
            }
        }
    }

    private void validatePendingCampaign(CampaignRequest request, boolean money, boolean goods) {
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
                throw new AppException(ErrorCode.CATEGORY_NOT_AVAILABLE, "Category '" + category.getName() + "' is not available");
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
}
