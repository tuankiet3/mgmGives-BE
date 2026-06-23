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
        validateDateRange(request);

        Set<Category> categories = fetchAndValidateCategories(request.categories());

        Campaign campaign = Campaign.builder()
                .title(request.title())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .target(request.target())
                .priority(request.priority())
                .status(CampaignStatus.PENDING)
                .user(currentUser)
                .categories(categories)
                .build();

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign created: id={}, title={}, userId={}", saved.getId(), saved.getTitle(), currentUser.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Campaign> getAllCampaigns(CampaignStatus status, CampaignPriority priority, Long categoryId,
            Long userId, String keyword, User currentUser, Pageable pageable) {
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

        return campaign;
    }

    @Override
    @Transactional
    public Campaign updateCampaign(Long id, CampaignRequest request, User currentUser) {
        Campaign campaign = getCampaignById(id, currentUser);

        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = campaign.getUser() != null && campaign.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isCreator) {
            log.warn("Update campaign denied: not admin/creator. campaignId={}, userId={}", id, currentUser.getId());
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE);
        }

        if (!isAdmin && campaign.getStatus() != CampaignStatus.PENDING
                && campaign.getStatus() != CampaignStatus.REJECTED) {
            log.warn("Update campaign denied: invalid status for non-admin. campaignId={}, status={}, userId={}",
                    id, campaign.getStatus(), currentUser.getId());
            throw new AppException(ErrorCode.INVALID_CAMPAIGN_STATUS_FOR_UPDATE);
        }

        if (!isAdmin && campaign.getStatus() == CampaignStatus.REJECTED) {
            log.info("Campaign status reset to PENDING after creator edit. campaignId={}", id);
            campaign.setStatus(CampaignStatus.PENDING);
        }

        validateDateRange(request);
        Set<Category> categories = fetchAndValidateCategories(request.categories());

        campaign.setTitle(request.title());
        campaign.setDescription(request.description());
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setTarget(request.target());
        campaign.setPriority(request.priority());
        campaign.setCategories(categories);

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign updated: id={}, status={}, userId={}", saved.getId(), saved.getStatus(), currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id) {
        Campaign campaign = getCampaignByIdInternal(id);
        campaignRepository.delete(campaign);
        log.info("Campaign deleted: id={}, title={}", id, campaign.getTitle());
    }

    private void validateDateRange(CampaignRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            log.warn("Campaign date validation failed: startDate={}, endDate={}",
                    request.startDate(), request.endDate());
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Start date must be before end date");
        }
    }

    private Set<Category> fetchAndValidateCategories(Set<Long> categoryIds) {
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
