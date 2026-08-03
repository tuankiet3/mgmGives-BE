package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign_label.CampaignLabelResponse;
import com.mgmtp.gives.dto.campaign_label.CreateCampaignLabelRequest;
import com.mgmtp.gives.dto.campaign_label.UpdateCampaignLabelRequest;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignTaskLabel;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignLabelRepository;
import com.mgmtp.gives.service.CampaignLabelService;
import com.mgmtp.gives.util.CampaignAccessHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignLabelServiceImpl implements CampaignLabelService {

    private final CampaignLabelRepository campaignLabelRepository;
    private final CampaignAccessHelper campaignAccessHelper;

    @Override
    @Transactional
    public CampaignLabelResponse createLabel(Long campaignId, CreateCampaignLabelRequest request, User currentUser) {
        Campaign campaign = campaignAccessHelper.findCampaignOrThrow(campaignId);
        campaignAccessHelper.validateCampaignAdmin(campaignId, currentUser, ErrorCode.UNAUTHORIZED_LABEL_ACCESS);

        CampaignTaskLabel label = CampaignTaskLabel.builder()
                .campaign(campaign)
                .name(request.name())
                .color(request.color())
                .build();

        CampaignTaskLabel savedLabel = campaignLabelRepository.save(label);

        log.info("Label created: campaignId={}, labelId={}, userId={}",
                campaignId, savedLabel.getId(), currentUser.getId());
        return toResponse(savedLabel);
    }

    @Override
    @Transactional
    public CampaignLabelResponse updateLabel(Long labelId, UpdateCampaignLabelRequest request, User currentUser) {
        CampaignTaskLabel label = findLabel(labelId);
        campaignAccessHelper.validateCampaignAdmin(label.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_LABEL_ACCESS);

        if (request.name() != null) {
            label.setName(request.name());
        }
        if (request.color() != null) {
            label.setColor(request.color());
        }

        CampaignTaskLabel savedLabel = campaignLabelRepository.save(label);

        log.info("Label updated: labelId={}, userId={}", labelId, currentUser.getId());
        return toResponse(savedLabel);
    }

    @Override
    @Transactional
    public void deleteLabel(Long labelId, User currentUser) {
        CampaignTaskLabel label = findLabel(labelId);
        campaignAccessHelper.validateCampaignAdmin(label.getCampaign().getId(), currentUser, ErrorCode.UNAUTHORIZED_LABEL_ACCESS);

        campaignLabelRepository.delete(label);
        log.info("Label deleted: labelId={}, userId={}", labelId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignLabelResponse> getLabelsByCampaign(Long campaignId, User currentUser) {
        campaignAccessHelper.findCampaignOrThrow(campaignId);
        campaignAccessHelper.validateCampaignMemberOrAdmin(
                campaignId, currentUser, ErrorCode.UNAUTHORIZED_LABEL_ACCESS);
        return campaignLabelRepository.findByCampaignId(campaignId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CampaignTaskLabel findLabel(Long labelId) {
        return campaignLabelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LABEL_NOT_FOUND));
    }

    private CampaignLabelResponse toResponse(CampaignTaskLabel label) {
        return new CampaignLabelResponse(
                label.getId(),
                label.getCampaign() != null ? label.getCampaign().getId() : null,
                label.getName(),
                label.getColor());
    }
}
