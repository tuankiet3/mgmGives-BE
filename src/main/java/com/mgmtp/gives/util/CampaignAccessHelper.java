package com.mgmtp.gives.util;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignMemberRepository;
import com.mgmtp.gives.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CampaignAccessHelper {

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository campaignMemberRepository;

    public Campaign findCampaignOrThrow(Long campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }

    public void validateCampaignAdmin(Long campaignId, User user, ErrorCode errorCodeToThrow) {
        if (!isCampaignAdmin(campaignId, user)) {
            throw new AppException(errorCodeToThrow);
        }
    }

    public boolean isCampaignAdmin(Long campaignId, User user) {
        if (user == null) return false;
        Campaign campaign = findCampaignOrThrow(campaignId);
        
        // Campaign owner is always admin
        if (campaign.getUser() != null && Objects.equals(campaign.getUser().getId(), user.getId())) {
            return true;
        }
        
        // Or user has CAMPAIGN_ADMIN role
        return campaignMemberRepository.existsByCampaignIdAndUserIdAndRoleInCampaign(
                campaignId, user.getId(), CampaignMemberRole.CAMPAIGN_ADMIN);
    }

    public boolean isCampaignMember(Long campaignId, Long userId) {
        return campaignMemberRepository.existsByCampaignIdAndUserId(campaignId, userId);
    }

    public void validateCampaignMemberOrAdmin(Long campaignId, User user, ErrorCode errorCodeToThrow) {
        if (user == null || (!isCampaignAdmin(campaignId, user)
                && !isCampaignMember(campaignId, user.getId()))) {
            throw new AppException(errorCodeToThrow);
        }
    }
}
