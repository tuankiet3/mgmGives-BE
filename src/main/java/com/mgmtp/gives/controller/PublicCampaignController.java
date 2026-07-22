package com.mgmtp.gives.controller;

import com.mgmtp.gives.common.ApiResponse;
import com.mgmtp.gives.common.PageResponse;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign.PublicCampaignResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.MediaContext;
import com.mgmtp.gives.mapper.CategoryMapper;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/campaigns")
@RequiredArgsConstructor
@Tag(name = "Public Campaigns", description = "Public read-only campaign discovery endpoints")
public class PublicCampaignController {

    private final CampaignService campaignService;
    private final CampaignMediaRepository campaignMediaRepository;
    private final DonationRepository donationRepository;
    private final CategoryMapper categoryMapper;

    @GetMapping
    @Operation(summary = "Get public campaigns", description = "Returns only campaigns visible to guests.")
    public ApiResponse<PageResponse<PublicCampaignResponse>> getPublicCampaigns(
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(required = false) CampaignPriority priority,
            @RequestParam(value = "categoryId", required = false) List<Long> categoryIds,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Campaign> campaignPage = campaignService.getAllCampaigns(
                status,
                priority,
                categoryIds,
                null,
                keyword,
                null,
                null,
                pageable);
        List<PublicCampaignResponse> dtoList = campaignPage.getContent().stream()
                .map(this::toPublicResponse)
                .toList();
        return ApiResponse.success(PageResponse.of(campaignPage, dtoList));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get public campaign details", description = "Returns public details for a visible campaign.")
    public ApiResponse<PublicCampaignResponse> getPublicCampaignById(@PathVariable Long id) {
        Campaign campaign = campaignService.getCampaignById(id, null);
        return ApiResponse.success(toPublicResponse(campaign));
    }

    private PublicCampaignResponse toPublicResponse(Campaign campaign) {
        List<CampaignMedia> activeMedia = campaignMediaRepository
                .findByCampaignIdAndContextNotAndDeletedAtIsNull(campaign.getId(), MediaContext.FINAL_REPORT);
        List<CampaignMediaResponse> mediaResponses = activeMedia.stream()
                .map(this::toMediaResponse)
                .toList();
        String coverImageUrl = activeMedia.stream()
                .filter(CampaignMedia::isCover)
                .map(CampaignMedia::getUrl)
                .findFirst()
                .orElse(null);

        return PublicCampaignResponse.builder()
                .id(campaign.getId())
                .title(campaign.getTitle())
                .description(campaign.getDescription())
                .status(campaign.getStatus())
                .startDate(campaign.getStartDate())
                .endDate(campaign.getEndDate())
                .target(campaign.getTarget())
                .currentRaised(donationRepository.sumConfirmedAmountByCampaignId(campaign.getId()))
                .priority(campaign.getPriority())
                .acceptsMoney(campaign.isAcceptsMoney())
                .acceptsGoods(campaign.isAcceptsGoods())
                .creatorName(campaign.getUser() != null ? campaign.getUser().getFullName() : null)
                .creatorAvatarUrl(campaign.getUser() != null ? campaign.getUser().getAvatarUrl() : null)
                .categories(campaign.getCategories().stream().map(categoryMapper::toResponse).toList())
                .medias(mediaResponses)
                .media(mediaResponses)
                .coverImageUrl(coverImageUrl)
                .volunteersCount(campaignService.getVolunteersCount(campaign.getId()))
                .donorsCount(campaignService.getDonorsCount(campaign.getId()))
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .build();
    }

    private CampaignMediaResponse toMediaResponse(CampaignMedia media) {
        return CampaignMediaResponse.builder()
                .id(media.getId())
                .url(media.getUrl())
                .mediaType(media.getMediaType())
                .isCover(media.isCover())
                .caption(media.getCaption())
                .displayOrder(media.getDisplayOrder())
                .context(media.getContext().name())
                .build();
    }
}
