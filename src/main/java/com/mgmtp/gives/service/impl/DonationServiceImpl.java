package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.dto.donation.*;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.notification.publisher.DonationNotificationPublisher;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignFollowerService;
import com.mgmtp.gives.service.DonationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import static com.mgmtp.gives.specification.DonationSpecifications.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonationServiceImpl implements DonationService {
    private final DonationNotificationPublisher publisher;
    private final CampaignFollowerService campaignFollowerService;
    private final DonationRepository donationRepository;
    private final CampaignRepository campaignRepository;

    @Override
    @Transactional
    public DonationResponse createDonation(DonationRequest request, User user) {
        log.info("Creating donation of type {} for campaign ID: {} by user: {}", 
                request.donationType(), request.campaignId(), user.getEmail());
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + request.campaignId()
                ));
        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(
                    ErrorCode.CAMPAIGN_NOT_IN_PROGRESS,
                    "Cannot donate to this campaign because it is currently " + campaign.getStatus()
            );
        }
        // Use detail directly, or fallback to goodsDescription for goods donation
        String detailText = request.detail();
        if (request.donationType() == DonationType.GOODS && request.goodsDescription() != null) {
            detailText = request.goodsDescription();
        }

        String messageText = (request.message() != null && org.springframework.util.StringUtils.hasText(request.message())) 
                ? request.message().trim() 
                : null;

        Donation donation = Donation.builder()
                .user(user)
                .campaign(campaign)
                .type(request.donationType())
                .amount(request.donationType() == DonationType.GOODS ? null : request.amount())
                .detail(detailText)
                .isAnonymous(request.anonymous())
                .status(DonationStatus.CONFIRMED)
                .confirmedAt(LocalDateTime.now())
                .transactionId(request.transactionId())
                .message(messageText)
                .isMessageHidden(false)
                .goodsCondition(request.donationType() == DonationType.GOODS ? request.goodsCondition() : null)
                .goodsCategory(request.donationType() == DonationType.GOODS ? request.goodsCategory() : null)
                .deliveryMethod(request.donationType() == DonationType.GOODS ? request.deliveryMethod() : null)
                .build();

        Donation savedDonation = donationRepository.save(donation);
        campaignFollowerService.autoFollow(user.getId(), campaign.getId());

        publisher.publishDonationConfirmedEvents(savedDonation);

        log.info(
                "Donation created and auto-confirmed: donationId={}, campaignId={}, donorUserId={}",
                savedDonation.getId(),
                campaign.getId(),
                user.getId()
        );

        return toResponse(savedDonation);
}

    @Override
    @Transactional(readOnly = true)
    public List<DonationResponse> getMyDonations(Long userId) {
        List<Donation> donations = donationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return donations.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DonationResponse> getPublicDonationsByCampaignId(Long campaignId) {
        List<Donation> donations = donationRepository.findByCampaignIdAndStatusNotOrderByCreatedAtDesc(
                campaignId, DonationStatus.FAILED);

        return donations.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DonationAdminResponse> getAllDonations(DonationStatus status, DonationType type, Long campaignId, Pageable pageable) {
        Specification<Donation> spec = Specification.allOf(
                hasStatus(status),
                hasType(type),
                hasCampaignId(campaignId)
        );
        return donationRepository.findAll(spec, pageable).map(this::toAdminResponse);
    }

    @Override
    @Transactional
    public DonationAdminResponse confirmDonation(Long donationId, User admin) {
        log.info("Admin {} is confirming donation ID: {}", admin.getEmail(), donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId
                ));

        donation.setStatus(DonationStatus.CONFIRMED);
        donation.setConfirmedBy(admin);
        donation.setConfirmedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        return toAdminResponse(savedDonation);
    }

    @Override
    @Transactional
    public VNPayResponse createVNPayDonation(VNPayRequest request, User user) {
        log.info("Creating VNPay donation for campaign ID: {} with amount: {} VND by user: {}", 
                request.campaignId(), request.amount(), user.getEmail());
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + request.campaignId()
                ));

        String txnRef = "VNP_MOCK_" + System.currentTimeMillis();

        String messageText = (request.message() != null && org.springframework.util.StringUtils.hasText(request.message())) 
                ? request.message().trim() 
                : null;

        Donation donation = Donation.builder()
                .user(user)
                .campaign(campaign)
                .type(DonationType.MONEY)
                .amount(request.amount())
                .detail("VNPay Donation")
                .isAnonymous(request.anonymous())
                .status(DonationStatus.PENDING)
                .transactionId(txnRef)
                .message(messageText)
                .isMessageHidden(false)
                .build();

        donation = donationRepository.save(donation);

        long amountInCents = request.amount() * 100L;
        String mockPaymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=" + amountInCents + "&vnp_TxnRef=" + donation.getId();
        String qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + mockPaymentUrl;

        return new VNPayResponse(donation.getId(), qrCodeUrl, request.amount(), txnRef);
    }

    @Override
    @Transactional
    public DonationResponse confirmVNPayDonation(Long donationId) {
        log.info("Confirming VNPay payment callback for donation ID: {}", donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId
                ));
        if (donation.getCampaign().getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(
                    ErrorCode.CAMPAIGN_NOT_IN_PROGRESS,
                    "Cannot donate to this campaign because it is not in progress yet"
            );
        }

        donation.setStatus(DonationStatus.CONFIRMED);
        donation.setConfirmedAt(LocalDateTime.now());
        donation.setUpdatedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        if (donation.getUser() != null && donation.getCampaign() != null) {
            campaignFollowerService.autoFollow(donation.getUser().getId(), donation.getCampaign().getId());
        }

        publisher.publishDonationConfirmedEvents(savedDonation);

        return toResponse(savedDonation);
    }

    @Override
    @Transactional
    public DonationResponse hideDonationMessage(Long donationId, boolean hidden, User currentUser) {
        log.info("User {} is setting hidden status to {} for donation message ID: {}", 
                currentUser.getEmail(), hidden, donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId
                ));

        boolean isAdmin = currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;
        boolean isCreator = donation.getCampaign().getUser() != null && 
                            donation.getCampaign().getUser().getId().equals(currentUser.getId());
        
        if (!isAdmin && !isCreator) {
            throw new com.mgmtp.gives.exception.AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE, 
                    "Only Campaign Admin or global ADMIN can moderate donation messages.");
        }

        donation.setMessageHidden(hidden);
        donation.setUpdatedAt(LocalDateTime.now());
        Donation saved = donationRepository.save(donation);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public DonationResponse cancelVNPayDonation(Long donationId) {
        log.info("Cancelling VNPay payment for donation ID: {}", donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId
                ));
        if (donation.getStatus() == DonationStatus.PENDING) {
            donation.setStatus(DonationStatus.FAILED);
            donation.setUpdatedAt(LocalDateTime.now());
            donation = donationRepository.save(donation);
        }
        return toResponse(donation);
    }

    private DonationAdminResponse toAdminResponse(Donation donation) {
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

    private DonationResponse toResponse(Donation donation) {
        String donorName = donation.isAnonymous() ? "Anonymous" : donation.getUser().getFullName();

        // Check if the current user is Campaign Admin or global ADMIN to display hidden message text
        boolean canSeeHidden = false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            com.mgmtp.gives.entity.User currentUser = userDetails.getUser();
            boolean isAdmin = currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;
            boolean isCreator = donation.getCampaign().getUser() != null &&
                    donation.getCampaign().getUser().getId().equals(currentUser.getId());
            if (isAdmin || isCreator) {
                canSeeHidden = true;
            }
        }

        String displayedMessage = donation.getMessage();
        if (donation.isMessageHidden() && !canSeeHidden) {
            displayedMessage = null;
        }

        return DonationResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .donorName(donorName)
                .type(donation.getType())
                .amount(donation.getAmount())
                .detail(donation.getDetail())
                .isAnonymous(donation.isAnonymous())
                .status(donation.getStatus())
                .transactionId(donation.getTransactionId())
                .rejectReason(donation.getRejectReason())
                .message(displayedMessage)
                .isMessageHidden(donation.isMessageHidden())
                .goodsCondition(donation.getGoodsCondition())
                .goodsCategory(donation.getGoodsCategory())
                .deliveryMethod(donation.getDeliveryMethod())
                .createdAt(donation.getCreatedAt())
                .build();
    }

    private String getDonorName(Donation donation) {
        if (donation.getUser() == null) {
            return "Unknown donor";
        }

        if (donation.getUser().getFullName() != null && !donation.getUser().getFullName().isBlank()) {
            return donation.getUser().getFullName();
        }

        return donation.getUser().getEmail();
    }

    private Long calculateNewRaised(Long oldRaised, Donation donation) {
        Long safeOldRaised = oldRaised == null ? 0L : oldRaised;

        if (donation.getType() != DonationType.MONEY || donation.getAmount() == null) {
            return safeOldRaised;
        }

        return safeOldRaised + donation.getAmount();
    }
}
