package com.mgmtp.gives.service.impl;

import com.mgmtp.gives.dto.donation.*;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.exception.ResourceNotFoundException;
import com.mgmtp.gives.repository.CampaignRepository;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.security.CustomUserDetails;
import com.mgmtp.gives.service.CampaignFollowerService;
import com.mgmtp.gives.service.DonationService;
import com.mgmtp.gives.service.NotificationService;
import com.mgmtp.gives.notification.publisher.DonationNotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.time.LocalDateTime;
import java.util.List;

import static com.mgmtp.gives.specification.DonationSpecifications.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DonationServiceImpl implements DonationService {

    private static final long MAX_DONATION_AMOUNT = 999_999_999_999L;

    private final DonationRepository donationRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignFollowerService campaignFollowerService;
    private final DonationNotificationPublisher publisher;
    private final NotificationService notificationService;
    private final com.mgmtp.gives.service.PayOSClientProvider payOSClientProvider;
    private final org.springframework.context.ApplicationContext applicationContext;

    @Value("${payos.cancel-url}")
    private String payOSCancelUrl;

    @Value("${payos.return-url}")
    private String payOSReturnUrl;

    @Override
    @Transactional
    public DonationResponse createDonation(DonationRequest request, User user) {
        log.info("Creating donation of type {} for campaign ID: {} by user: {}",
                request.donationType(), request.campaignId(), user.getEmail());
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + request.campaignId()));
        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(
                    ErrorCode.CAMPAIGN_NOT_IN_PROGRESS,
                    "Cannot donate to this campaign because it is currently " + campaign.getStatus());
        }
        if (request.donationType() == DonationType.MONEY) {
            validateMoneyAmount(request.amount());
        }
        String detailText = request.detail();
        if (request.donationType() == DonationType.GOODS && request.goodsDescription() != null) {
            detailText = request.goodsDescription();
        }

        String messageText = (request.message() != null
                && org.springframework.util.StringUtils.hasText(request.message()))
                        ? request.message().trim()
                        : null;

        Donation donation = Donation.builder()
                .user(user)
                .campaign(campaign)
                .type(request.donationType())
                .amount(request.donationType() == DonationType.GOODS ? null : request.amount())
                .detail(detailText)
                .isAnonymous(request.anonymous())
                .status(DonationStatus.SUCCESSFUL)
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
        notificationService.broadcastDashboardUpdate();

        log.info(
                "Donation created and auto-confirmed: donationId={}, campaignId={}, donorUserId={}",
                savedDonation.getId(),
                campaign.getId(),
                user.getId());

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
    public Page<DonationAdminResponse> getAllDonations(DonationStatus status, DonationType type, Long campaignId,
            Pageable pageable) {
        Specification<Donation> spec = Specification.allOf(
                hasStatus(status),
                hasType(type),
                hasCampaignId(campaignId));
        return donationRepository.findAll(spec, pageable).map(this::toAdminResponse);
    }

    @Override
    @Transactional
    public DonationAdminResponse confirmDonation(Long donationId, User admin) {
        log.info("Admin {} is confirming donation ID: {}", admin.getEmail(), donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId));

        donation.setStatus(DonationStatus.SUCCESSFUL);
        donation.setConfirmedBy(admin);
        donation.setConfirmedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        return toAdminResponse(savedDonation);
    }

    @Override
    public PayOSResponse createPayOSDonation(PayOSRequest request, User user) {
        log.info("Creating PayOS donation for campaign ID: {} with amount: {} VND by user: {}",
                request.campaignId(), request.amount(), user.getEmail());
        Campaign campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CAMPAIGN_NOT_FOUND,
                        "Campaign not found with ID: " + request.campaignId()));

        if (campaign.getStatus() != CampaignStatus.IN_PROGRESS) {
            throw new AppException(
                    ErrorCode.CAMPAIGN_NOT_IN_PROGRESS,
                    "Cannot donate to this campaign because it is currently " + campaign.getStatus());
        }

        validateMoneyAmount(request.amount());

        String messageText = (request.message() != null
                && org.springframework.util.StringUtils.hasText(request.message()))
                        ? request.message().trim()
                        : null;

        Donation donation = Donation.builder()
                .user(user)
                .campaign(campaign)
                .type(DonationType.MONEY)
                .amount(request.amount())
                .detail("PayOS Donation")
                .isAnonymous(request.anonymous())
                .status(DonationStatus.PENDING)
                .message(messageText)
                .isMessageHidden(false)
                .build();

        donation = donationRepository.save(donation);

        try {
            String description = "mgm Gives " + donation.getId();
            String cancelUrl = payOSCancelUrl + "/campaigns/" + campaign.getId()
                    + "/donate?paymentStatus=cancel&donationId=" + donation.getId();
            String returnUrl = payOSReturnUrl + "/campaigns/" + campaign.getId() + "?payment=success&donationId="
                    + donation.getId();

            long orderCode = System.currentTimeMillis() * 1000 + (donation.getId() % 1000);
            CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(request.amount())
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            PayOS activePayOS = payOSClientProvider.getClientForCampaign(campaign);
            CreatePaymentLinkResponse checkoutResponse = activePayOS.paymentRequests().create(paymentData);

            donation.setTransactionId(checkoutResponse.getPaymentLinkId());
            donation.setOrderCode(orderCode);
            donationRepository.save(donation);

            log.info("PayOS payment link created for donation ID: {}", donation.getId());
            return new PayOSResponse(
                    donation.getId(),
                    checkoutResponse.getCheckoutUrl(),
                    request.amount(),
                    checkoutResponse.getQrCode(),
                    checkoutResponse.getBin(),
                    checkoutResponse.getAccountNumber(),
                    checkoutResponse.getAccountName(),
                    checkoutResponse.getDescription()
            );
        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for donation ID: {}", donation.getId(), e);
            // Clean up the pending donation if PayOS fails
            donation.setStatus(DonationStatus.FAILED);
            donationRepository.save(donation);
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Failed to create payment link. Please check your PayOS credentials or try again later. Details: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public DonationResponse confirmPayOSDonation(Long donationId) {
        log.info("Confirming PayOS payment for donation ID: {}", donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId));

        if (donation.getStatus() != DonationStatus.PENDING) {
            log.warn("Donation ID {} is already in status {}; ignoring duplicate webhook.", donationId,
                    donation.getStatus());
            return toResponse(donation);
        }

        donation.setStatus(DonationStatus.SUCCESSFUL);
        donation.setConfirmedAt(LocalDateTime.now());
        donation.setUpdatedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        if (donation.getUser() != null && donation.getCampaign() != null) {
            campaignFollowerService.autoFollow(donation.getUser().getId(), donation.getCampaign().getId());
        }

        publisher.publishDonationConfirmedEvents(savedDonation);
        notificationService.broadcastDashboardUpdate();

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
                        "Donation not found with ID: " + donationId));

        boolean isAdmin = currentUser.getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;
        boolean isCreator = donation.getCampaign().getUser() != null &&
                donation.getCampaign().getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isCreator) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only Campaign Admin or global ADMIN can moderate donation messages.");
        }

        donation.setMessageHidden(hidden);
        donation.setUpdatedAt(LocalDateTime.now());
        Donation saved = donationRepository.save(donation);

        notificationService.broadcastDashboardUpdate();

        return toResponse(saved);
    }

    @Override
    @Transactional
    public DonationResponse cancelPayOSDonation(Long donationId) {
        log.info("Cancelling PayOS payment for donation ID: {}", donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId));
        validateDonationOwnership(donation);
        if (donation.getStatus() == DonationStatus.PENDING) {
            donation.setStatus(DonationStatus.FAILED);
            donation.setUpdatedAt(LocalDateTime.now());
            donation = donationRepository.save(donation);
            notificationService.broadcastDashboardUpdate();
        }
        return toResponse(donation);
    }

    @Override
    @Transactional
    public DonationResponse confirmPayOSDonationByPaymentLinkId(String paymentLinkId) {
        log.info("Confirming PayOS payment for paymentLinkId: {}", paymentLinkId);
        Donation donation = donationRepository.findByTransactionId(paymentLinkId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with paymentLinkId: " + paymentLinkId));

        if (donation.getStatus() != DonationStatus.PENDING) {
            log.warn("Donation with paymentLinkId {} is already in status {}; ignoring duplicate webhook.",
                    paymentLinkId, donation.getStatus());
            return toResponse(donation);
        }

        donation.setStatus(DonationStatus.SUCCESSFUL);
        donation.setConfirmedAt(LocalDateTime.now());
        donation.setUpdatedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        if (donation.getUser() != null && donation.getCampaign() != null) {
            campaignFollowerService.autoFollow(donation.getUser().getId(), donation.getCampaign().getId());
        }

        publisher.publishDonationConfirmedEvents(savedDonation);
        notificationService.broadcastDashboardUpdate();

        return toResponse(savedDonation);
    }

    @Override
    public DonationResponse verifyPayOSUserTransaction(Long donationId) {
        log.info("Active verify verification requested for donation ID: {}", donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId
                ));

        validateDonationOwnership(donation);

        if (donation.getStatus() == DonationStatus.SUCCESSFUL) {
            return toResponse(donation);
        }

        if (donation.getTransactionId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "No payment request exists for this donation.");
        }

        try {
            PayOS activePayOS = payOSClientProvider.getClientForCampaign(donation.getCampaign());
            vn.payos.model.v2.paymentRequests.PaymentLink paymentLink = activePayOS.paymentRequests().get(donation.getTransactionId());
            if (paymentLink != null) {
                vn.payos.model.v2.paymentRequests.PaymentLinkStatus status = paymentLink.getStatus();
                if (vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID.equals(status)) {
                    DonationService self = applicationContext.getBean(DonationService.class);
                    return self.confirmPayOSDonationByPaymentLinkId(donation.getTransactionId());
                } else if (vn.payos.model.v2.paymentRequests.PaymentLinkStatus.CANCELLED.equals(status) ||
                           vn.payos.model.v2.paymentRequests.PaymentLinkStatus.EXPIRED.equals(status) ||
                           vn.payos.model.v2.paymentRequests.PaymentLinkStatus.FAILED.equals(status)) {
                    donation.setStatus(DonationStatus.FAILED);
                    donation.setUpdatedAt(LocalDateTime.now());
                    donationRepository.save(donation);
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "This donation payment has been cancelled, expired or failed on PayOS.");
                }
            }
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Transaction has not been paid successfully on PayOS yet. Please try again later.");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check active payment status for donation ID: {}", donationId, e);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Failed to connect to PayOS to verify transaction: " + e.getMessage());
        }
    }

    /**
     * Validates that the currently authenticated user is the owner of this
     * donation.
     * Throws UNAUTHORIZED if the caller does not match the donation owner.
     */
    private void validateDonationOwnership(Donation donation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }

        Long currentUserId = userDetails.getUser().getId();
        boolean isAdmin = userDetails.getUser().getRole() == com.mgmtp.gives.enums.UserRole.ADMIN;

        if (!isAdmin && (donation.getUser() == null || !donation.getUser().getId().equals(currentUserId))) {
            log.warn("Ownership check failed: donationId={}, donationOwnerId={}, requestUserId={}",
                    donation.getId(),
                    donation.getUser() != null ? donation.getUser().getId() : null,
                    currentUserId);
            throw new AppException(ErrorCode.UNAUTHORIZED, "You do not have permission to modify this donation");
        }
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

        boolean canSeeHidden = false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            User currentUser = userDetails.getUser();
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

    private void validateMoneyAmount(Long amount) {
        if (amount == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Amount is required for money donations");
        }
        if (amount <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Amount must be greater than zero");
        }
        if (amount > MAX_DONATION_AMOUNT) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Amount must not exceed " + MAX_DONATION_AMOUNT);
        }
    }

}
