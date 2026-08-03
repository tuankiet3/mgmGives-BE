package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign.CampaignMediaResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingListResponse;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingResponse;
import com.mgmtp.gives.dto.campaign_spending.CreateCampaignSpendingRequest;
import com.mgmtp.gives.dto.campaign_spending.UpdateCampaignSpendingRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignSpending;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignSpendingRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.CampaignSpendingService;
import com.mgmtp.gives.service.MediaService;
import com.mgmtp.gives.util.CampaignAccessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSpendingServiceImpl implements CampaignSpendingService {

    private final CampaignSpendingRepository campaignSpendingRepository;
    private final CampaignMediaRepository campaignMediaRepository;
    private final DonationRepository donationRepository;
    private final CampaignAccessHelper campaignAccessHelper;
    private final MediaService mediaService;

    @Override
    @Transactional
    public CampaignSpendingResponse createSpending(Long campaignId, CreateCampaignSpendingRequest request, User currentUser) {
        Campaign campaign = campaignAccessHelper.findCampaignOrThrow(campaignId);
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_SPENDING_ACCESS);

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.CAMPAIGN_NOT_IN_PROGRESS_FOR_SPENDING);
        }
        validateSpentAt(request.spentAt(), campaign);

        CampaignSpending spending = CampaignSpending.builder()
                .campaign(campaign)
                .amount(request.amount())
                .description(request.description())
                .spentAt(request.spentAt())
                .createdBy(currentUser)
                .build();

        CampaignSpending saved = campaignSpendingRepository.save(spending);
        log.info("Spending created: campaignId={}, spendingId={}, userId={}", campaignId, saved.getId(), currentUser.getId());
        return toResponse(saved, List.of());
    }

    @Override
    @Transactional
    public CampaignSpendingResponse updateSpending(Long spendingId, UpdateCampaignSpendingRequest request, User currentUser) {
        CampaignSpending spending = findSpending(spendingId);
        Campaign campaign = spending.getCampaign();
        campaignAccessHelper.validateCampaignAdmin(campaign.getId(), currentUser, ErrorCode.UNAUTHORIZED_SPENDING_ACCESS);

        if (request.description() != null && request.description().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Spending description cannot be blank");
        }

        // Only stamp updatedAt (which the UI surfaces as an "Edited" badge, per the transparency
        // rule that edits must be visible) when a field is actually changing - a no-op save
        // (e.g. submitted only to persist a photo added in the same dialog) must not falsely
        // flag an untouched entry as edited.
        boolean changed = false;
        if (request.spentAt() != null) {
            validateSpentAt(request.spentAt(), campaign);
            if (!request.spentAt().equals(spending.getSpentAt())) {
                spending.setSpentAt(request.spentAt());
                changed = true;
            }
        }
        if (request.amount() != null && !request.amount().equals(spending.getAmount())) {
            spending.setAmount(request.amount());
            changed = true;
        }
        if (request.description() != null && !request.description().equals(spending.getDescription())) {
            spending.setDescription(request.description());
            changed = true;
        }

        if (changed) {
            spending.setUpdatedAt(LocalDateTime.now());
        }
        CampaignSpending saved = campaignSpendingRepository.save(spending);
        log.info("Spending updated: spendingId={}, userId={}", spendingId, currentUser.getId());
        return toResponse(saved, activePhotos(saved.getId()));
    }

    @Override
    @Transactional
    public void deleteSpending(Long spendingId, User currentUser) {
        CampaignSpending spending = findSpending(spendingId);
        Campaign campaign = spending.getCampaign();
        campaignAccessHelper.validateCampaignAdmin(campaign.getId(), currentUser, ErrorCode.UNAUTHORIZED_SPENDING_ACCESS);

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.SPENDING_DELETE_NOT_ALLOWED);
        }

        spending.setDeletedAt(LocalDateTime.now());
        campaignSpendingRepository.save(spending);
        log.info("Spending deleted (soft): spendingId={}, userId={}", spendingId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignSpendingListResponse getSpendingsByCampaign(Long campaignId) {
        return getSpendingsByCampaign(campaignId, donationRepository.sumConfirmedAmountByCampaignId(campaignId));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignSpendingListResponse getSpendingsByCampaign(Long campaignId, long totalRaised) {
        campaignAccessHelper.findCampaignOrThrow(campaignId);

        List<CampaignSpending> spendings = campaignSpendingRepository.findActiveByCampaignId(campaignId);
        List<Long> spendingIds = spendings.stream().map(CampaignSpending::getId).toList();
        Map<Long, List<CampaignMedia>> photosBySpendingId = spendingIds.isEmpty()
                ? Map.of()
                : campaignMediaRepository.findBySpendingIdInAndDeletedAtIsNull(spendingIds).stream()
                        .collect(Collectors.groupingBy(m -> m.getSpending().getId()));

        List<CampaignSpendingResponse> items = spendings.stream()
                .map(s -> toResponse(s, photosBySpendingId.getOrDefault(s.getId(), List.of())))
                .toList();

        long totalSpent = campaignSpendingRepository.sumAmountByCampaignId(campaignId);

        return new CampaignSpendingListResponse(items, totalRaised, totalSpent, totalRaised - totalSpent);
    }

    @Override
    @Transactional
    public CampaignSpendingResponse addPhoto(Long spendingId, MultipartFile file, User currentUser) {
        CampaignSpending spending = findSpending(spendingId);
        Campaign campaign = spending.getCampaign();
        campaignAccessHelper.validateCampaignAdmin(campaign.getId(), currentUser, ErrorCode.UNAUTHORIZED_SPENDING_ACCESS);

        mediaService.uploadCampaignSpendingAttachment(file, campaign, spending);
        log.info("Spending photo added: spendingId={}, userId={}", spendingId, currentUser.getId());
        return toResponse(spending, activePhotos(spendingId));
    }

    @Override
    @Transactional
    public CampaignSpendingResponse removePhoto(Long spendingId, Long mediaId, User currentUser) {
        CampaignSpending spending = findSpending(spendingId);
        Campaign campaign = spending.getCampaign();
        campaignAccessHelper.validateCampaignAdmin(campaign.getId(), currentUser, ErrorCode.UNAUTHORIZED_SPENDING_ACCESS);

        CampaignMedia media = campaignMediaRepository.findByIdAndSpendingIdAndDeletedAtIsNull(mediaId, spendingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_MEDIA_NOT_FOUND));

        mediaService.softDeleteCampaignSpendingAttachment(media);
        log.info("Spending photo removed: spendingId={}, mediaId={}, userId={}", spendingId, mediaId, currentUser.getId());
        return toResponse(spending, activePhotos(spendingId));
    }

    private CampaignSpending findSpending(Long spendingId) {
        CampaignSpending spending = campaignSpendingRepository.findById(spendingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SPENDING_NOT_FOUND));
        if (spending.getDeletedAt() != null) {
            throw new ResourceNotFoundException(ErrorCode.SPENDING_NOT_FOUND);
        }
        return spending;
    }

    private List<CampaignMedia> activePhotos(Long spendingId) {
        return campaignMediaRepository.findBySpendingIdAndDeletedAtIsNull(spendingId);
    }

    private void validateSpentAt(LocalDate spentAt, Campaign campaign) {
        if (campaign.getStartDate() != null && spentAt.isBefore(campaign.getStartDate().toLocalDate())) {
            throw new AppException(ErrorCode.SPENDING_DATE_BEFORE_CAMPAIGN_START);
        }
        if (spentAt.isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.SPENDING_DATE_IN_FUTURE);
        }
    }

    private CampaignSpendingResponse toResponse(CampaignSpending spending, List<CampaignMedia> photos) {
        User creator = spending.getCreatedBy();
        CampaignSpendingResponse.UserSummary createdBy = creator == null ? null
                : new CampaignSpendingResponse.UserSummary(
                        creator.getId(),
                        creator.getFullName(),
                        creator.getEmail(),
                        creator.getAvatarUrl());

        List<CampaignMediaResponse> photoResponses = photos.stream()
                .sorted(Comparator.comparing(CampaignMedia::getId))
                .map(m -> new CampaignMediaResponse(m.getId(), m.getUrl(), m.getMediaType(), m.isCover(),
                        m.getCaption(), m.getDisplayOrder(), m.getContext().name()))
                .toList();

        return new CampaignSpendingResponse(
                spending.getId(),
                spending.getCampaign().getId(),
                spending.getAmount(),
                spending.getDescription(),
                spending.getSpentAt(),
                createdBy,
                photoResponses,
                spending.getCreatedAt(),
                spending.getUpdatedAt());
    }
}
