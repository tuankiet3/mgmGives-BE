package com.mgmtp.gives.mapper;

import com.mgmtp.gives.dto.donation.DonationAdminResponse;
import com.mgmtp.gives.dto.donation.DonationResponse;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.UserRole;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DonationResponseMapper {

    private final CampaignMemberService campaignMemberService;

    public DonationAdminResponse toAdminResponse(Donation donation) {
        return DonationAdminResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .userId(donation.getUser().getId())
                .userName(donation.getUser().getFullName())
                .userEmail(donation.getUser().getEmail())
                .type(donation.getType())
                .amount(donation.getAmount())
                .detail(donation.getDetail())
                .isAnonymous(donation.isAnonymous())
                .status(donation.getStatus())
                .transactionId(donation.getTransactionId())
                .transactionDescription(donation.getTransactionDescription())
                .transactionProofUrl(donation.getTransactionProofUrl())
                .confirmedById(donation.getConfirmedBy() != null ? donation.getConfirmedBy().getId() : null)
                .confirmedByName(donation.getConfirmedBy() != null ? donation.getConfirmedBy().getFullName() : null)
                .confirmedAt(donation.getConfirmedAt())
                .rejectReason(donation.getRejectReason())
                .message(donation.getMessage())
                .isMessageHidden(donation.isMessageHidden())
                .goodsCondition(donation.getGoodsCondition())
                .goodsCategory(donation.getGoodsCategory())
                .deliveryMethod(donation.getDeliveryMethod())
                .createdAt(donation.getCreatedAt())
                .updatedAt(donation.getUpdatedAt())
                .build();
    }

    public DonationResponse toResponse(Donation donation) {
        boolean canSeeHidden = canSeeHiddenDetails(donation);
        String donorName = donation.isAnonymous() ? "Anonymous" : donation.getUser().getFullName();
        String donorEmail = donation.isAnonymous() && !canSeeHidden
                ? null
                : donation.getUser() != null ? donation.getUser().getEmail() : null;
        String displayedMessage = donation.isMessageHidden() && !canSeeHidden ? null : donation.getMessage();

        return DonationResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .donorName(donorName)
                .donorEmail(donorEmail)
                .type(donation.getType())
                .amount(canSeeHidden ? donation.getAmount() : null)
                .detail(donation.getDetail())
                .isAnonymous(donation.isAnonymous())
                .status(donation.getStatus())
                .transactionId(donation.getTransactionId())
                .transactionDescription(donation.getTransactionDescription())
                .transactionProofUrl(donation.getTransactionProofUrl())
                .rejectReason(donation.getRejectReason())
                .message(displayedMessage)
                .isMessageHidden(donation.isMessageHidden())
                .goodsCondition(donation.getGoodsCondition())
                .goodsCategory(donation.getGoodsCategory())
                .deliveryMethod(donation.getDeliveryMethod())
                .confirmedAt(donation.getConfirmedAt())
                .updatedAt(donation.getUpdatedAt())
                .createdAt(donation.getCreatedAt())
                .build();
    }

    private boolean canSeeHiddenDetails(Donation donation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return false;
        }

        User currentUser = userDetails.getUser();
        boolean isAdmin = currentUser.getRole() == UserRole.ADMIN;
        boolean isCreator = donation.getCampaign().getUser() != null
                && donation.getCampaign().getUser().getId().equals(currentUser.getId());
        boolean isDonor = donation.getUser() != null && donation.getUser().getId().equals(currentUser.getId());
        boolean isCampaignManager = campaignMemberService.canManageCampaign(
                donation.getCampaign().getId(), currentUser);
        return isAdmin || isCreator || isDonor || isCampaignManager;
    }
}
