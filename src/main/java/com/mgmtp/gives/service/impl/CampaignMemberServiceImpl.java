package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.campaign_member.CampaignMemberResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.service.CampaignFollowerService;
import com.mgmtp.gives.service.CampaignMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service @RequiredArgsConstructor @Slf4j
public class CampaignMemberServiceImpl implements CampaignMemberService {

    private final CampaignMemberRepository campaignMemberRepo;
    private final CampaignRepository campaignRepo;
    private final CampaignFollowerService campaignFollowerService;

    @Transactional @Override
    public CampaignMemberResponse joinCampaign(User user, Long campaignId) {
        Campaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new AppException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS
                && campaign.getStatus() != CampaignStatus.APPROVED) {
            throw new AppException(ErrorCode.CAMPAIGN_NOT_IN_PROGRESS);
        }

        CampaignMember existing = campaignMemberRepo.findByCampaignIdAndUserId(campaignId, user.getId())
                .orElse(null);
        if (existing != null) {
            return new CampaignMemberResponse(
                    existing.getId(),
                    campaignId,
                    user.getId(),
                    existing.getRoleInCampaign().name(),
                    existing.getJoinedAt()
            );
        }

        CampaignMember member = CampaignMember.builder()
                .campaign(campaign)
                .user(user)
                .roleInCampaign(CampaignMemberRole.VOLUNTEER)
                .joinedAt(LocalDateTime.now())
                .build();

        CampaignMember saved;
        try {
            saved = campaignMemberRepo.save(member);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.CAMPAIGN_ALREADY_JOINED);
        }

        try {
            campaignFollowerService.autoFollow(user.getId(), campaignId);
        } catch (DataAccessException e) {
            log.warn("Auto-follow failed after join: userId={}, campaignId={}", user.getId(), campaignId, e);
        }

        log.info("User joined campaign: userId={}, campaignId={}", user.getId(), campaignId);

        return new CampaignMemberResponse(
                saved.getId(),
                campaignId,
                user.getId(),
                saved.getRoleInCampaign().name(),
                saved.getJoinedAt()
        );
    }

    @Transactional @Override
    public void unjoinCampaign(User user, Long campaignId) {
        long deleted = campaignMemberRepo.deleteByCampaignIdAndUserId(campaignId, user.getId());

        if (deleted > 0) {
            log.info("User unjoined campaign: userId={}, campaignId={}", user.getId(), campaignId);
        } else {
            log.debug("Unjoin skipped because member record does not exist: userId={}, campaignId={}", user.getId(), campaignId);
        }
    }

    @Transactional(readOnly = true) @Override
    public boolean canManageCampaign(Long campaignId, User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }
        return campaignMemberRepo.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaignId, user.getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
    }
}