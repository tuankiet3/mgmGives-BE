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
import com.mgmtp.gives.service.CampaignMemberService;
import com.mgmtp.gives.service.DonationService;
import com.mgmtp.gives.service.NotificationService;
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
import java.util.Optional;

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
    private final CampaignMemberService campaignMemberService;
    private final com.mgmtp.gives.repository.CampaignMemberRepository campaignMemberRepository;
    private final com.mgmtp.gives.service.PayOSClientProvider payOSClientProvider;
    private final org.springframework.context.ApplicationContext applicationContext;

    @Value("${payos.cancel-url}")
    private String payOSCancelUrl;

    @Value("${payos.return-url}")
    private String payOSReturnUrl;

    @Override
    @Transactional
    public DonationResponse createDonation(DonationRequest request, User user, String idempotencyKey) {
        log.info("Creating donation of type {} for campaign ID: {} by user: {} with idempotencyKey: {}",
                request.donationType(), request.campaignId(), user.getEmail(), idempotencyKey);

        if (org.springframework.util.StringUtils.hasText(idempotencyKey)) {
            Optional<Donation> existingDonationOpt = donationRepository.findByIdempotencyKey(idempotencyKey);
            if (existingDonationOpt.isPresent()) {
                Donation existing = existingDonationOpt.get();
                boolean matches = existing.getCampaign().getId().equals(request.campaignId())
                        && existing.getType() == request.donationType()
                        && (request.donationType() == DonationType.MONEY 
                            ? (request.amount() != null && request.amount().equals(existing.getAmount())) 
                            : true)
                        && existing.isAnonymous() == request.anonymous()
                        && existing.getUser().getId().equals(user.getId());

                if (matches) {
                    log.info("Duplicate request detected with matching idempotency key: {}. Returning original donation ID: {}",
                            idempotencyKey, existing.getId());
                    return toResponse(existing);
                } else {
                    log.warn("Duplicate request detected with conflicting parameters for idempotency key: {}", idempotencyKey);
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "Idempotency key has already been used with different request parameters.");
                }
            }
        }

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

        boolean isManualMoney = request.donationType() == DonationType.MONEY;
        if (isManualMoney && campaign.getDonationMethod() == com.mgmtp.gives.enums.DonationMethod.PAYOS) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "This campaign only accepts PayOS online payments, not manual QR bank transfers.");
        }
        DonationStatus status = isManualMoney ? DonationStatus.PENDING : DonationStatus.SUCCESSFUL;
        LocalDateTime confirmedAt = isManualMoney ? null : LocalDateTime.now();

        Donation donation = Donation.builder()
                .user(user)
                .campaign(campaign)
                .type(request.donationType())
                .amount(request.donationType() == DonationType.GOODS ? null : request.amount())
                .detail(detailText)
                .isAnonymous(request.anonymous())
                .status(status)
                .confirmedAt(confirmedAt)
                .transactionId(request.transactionId())
                .transactionDescription(null)
                .transactionProofUrl(request.transactionProofUrl())
                .message(messageText)
                .isMessageHidden(false)
                .goodsCondition(request.donationType() == DonationType.GOODS ? request.goodsCondition() : null)
                .goodsCategory(request.donationType() == DonationType.GOODS ? request.goodsCategory() : null)
                .deliveryMethod(request.donationType() == DonationType.GOODS ? request.deliveryMethod() : null)
                .idempotencyKey(idempotencyKey)
                .build();

        Donation savedDonation = donationRepository.save(donation);
        if (isManualMoney) {
            savedDonation.setTransactionDescription("mgmGives " + savedDonation.getId());
            savedDonation = donationRepository.save(savedDonation);
        }
        campaignFollowerService.autoFollow(user.getId(), campaign.getId());

        if (status == DonationStatus.SUCCESSFUL) {
            publisher.publishDonationConfirmedEvents(savedDonation);
            notificationService.broadcastDashboardUpdate();
        } else {
            sendPendingApprovalNotification(savedDonation);
            notificationService.broadcastDashboardUpdate();
        }

        log.info(
                "Donation created: donationId={}, campaignId={}, status={}",
                savedDonation.getId(),
                campaign.getId(),
                status);

        return toResponse(savedDonation);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DonationResponse> getMyDonations(Long userId, DonationStatus status, DonationType type,
            Boolean anonymous, String search, Pageable pageable) {
        Specification<Donation> spec = Specification.allOf(
                hasUserId(userId),
                hasStatus(status),
                hasType(type),
                isAnonymous(anonymous),
                matchesSearch(search));
        return donationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DonationResponse> getPublicDonationsByCampaignId(Long campaignId) {
        List<Donation> donations = donationRepository.findByCampaignIdAndStatusNotInOrderByCreatedAtDesc(
                campaignId, List.of(DonationStatus.FAILED, DonationStatus.REJECTED));

        return donations.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DonationAdminResponse> getAllDonations(DonationStatus status, DonationType type, Long campaignId,
            String search, Pageable pageable) {
        Specification<Donation> spec = Specification.allOf(
                hasStatus(status),
                hasType(type),
                hasCampaignId(campaignId),
                matchesSearch(search));
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

        if (donation.getStatus() == DonationStatus.SUCCESSFUL) {
            return toAdminResponse(donation);
        }

        donation.setStatus(DonationStatus.SUCCESSFUL);
        donation.setRejectReason(null);
        donation.setConfirmedBy(admin);
        donation.setConfirmedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        publisher.publishDonationConfirmedEvents(savedDonation);
        notificationService.broadcastDashboardUpdate();

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

        if (campaign.getDonationMethod() == com.mgmtp.gives.enums.DonationMethod.MANUAL_QR) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "This campaign does not accept PayOS payments.");
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
            String description = "mgmGives " + donation.getId();
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
            donation.setTransactionDescription(description);
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
                    "Failed to create PayOS payment link. Please ensure the campaign creator's PayOS integration credentials are valid and active.");
        }
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

        if (!campaignMemberService.canManageCampaign(donation.getCampaign().getId(), currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only a Campaign Admin can moderate donation messages.");
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
        donation.setRejectReason(null);
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
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Failed to connect to PayOS to verify transaction. Please check your internet connection or try again later.");
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
            boolean isDonor = donation.getUser() != null && donation.getUser().getId().equals(currentUser.getId());
            boolean isCampaignManager = campaignMemberService.canManageCampaign(donation.getCampaign().getId(), currentUser);
            if (isAdmin || isCreator || isDonor || isCampaignManager) {
                canSeeHidden = true;
            }
        }

        String displayedMessage = donation.getMessage();
        if (donation.isMessageHidden() && !canSeeHidden) {
            displayedMessage = null;
        }

        Long amountVal = canSeeHidden ? donation.getAmount() : null;
        String donorEmail = donation.isAnonymous() && !canSeeHidden ? null : (donation.getUser() != null ? donation.getUser().getEmail() : null);

        return DonationResponse.builder()
                .id(donation.getId())
                .campaignId(donation.getCampaign().getId())
                .campaignName(donation.getCampaign().getTitle())
                .donorName(donorName)
                .donorEmail(donorEmail)
                .type(donation.getType())
                .amount(amountVal)
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

    @Override
    @Transactional
    public DonationResponse confirmCampaignDonation(Long donationId, User currentUser) {
        log.info("User {} is confirming campaign donation ID: {}", currentUser.getEmail(), donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId));

        if (!campaignMemberService.canManageCampaign(donation.getCampaign().getId(), currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only a Campaign Admin can confirm manual QR donations.");
        }

        if (donation.getStatus() == DonationStatus.SUCCESSFUL) {
            return toResponse(donation);
        }

        donation.setStatus(DonationStatus.SUCCESSFUL);
        donation.setRejectReason(null);
        donation.setConfirmedBy(currentUser);
        donation.setConfirmedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        publisher.publishDonationConfirmedEvents(savedDonation);
        notificationService.broadcastDashboardUpdate();

        return toResponse(savedDonation);
    }

    @Override
    @Transactional
    public DonationResponse rejectCampaignDonation(Long donationId, String reason, User currentUser) {
        log.info("User {} is rejecting campaign donation ID: {} with reason: {}", currentUser.getEmail(), donationId, reason);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId));

        if (!campaignMemberService.canManageCampaign(donation.getCampaign().getId(), currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only a Campaign Admin can reject manual QR donations.");
        }

        if (donation.getStatus() == DonationStatus.REJECTED) {
            return toResponse(donation);
        }

        String finalReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Invalid transaction details";

        donation.setStatus(DonationStatus.REJECTED);
        donation.setRejectReason(finalReason);
        donation.setConfirmedAt(LocalDateTime.now());
        donation.setUpdatedAt(LocalDateTime.now());
        Donation savedDonation = donationRepository.save(donation);

        notificationService.broadcastDashboardUpdate();
        sendRejectionNotifications(savedDonation, finalReason);

        return toResponse(savedDonation);
    }

    @Override
    @Transactional
    public DonationResponse editCampaignDonation(Long donationId, EditDonationRequest request, User currentUser) {
        log.info("User {} is editing campaign donation ID: {}", currentUser.getEmail(), donationId);
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DONATE_NOT_FOUND,
                        "Donation not found with ID: " + donationId));

        if (!campaignMemberService.canManageCampaign(donation.getCampaign().getId(), currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only a Campaign Admin can edit donation details.");
        }

        if (donation.getStatus() != DonationStatus.PENDING && donation.getStatus() != DonationStatus.FAILED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Only pending or cancelled donations can be edited.");
        }

        if (donation.getType() == DonationType.MONEY && donation.getOrderCode() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "PayOS donations cannot be edited.");
        }

        String note = (request.reason() != null) ? request.reason().trim() : "";

        if (donation.getStatus() == DonationStatus.PENDING) {
            donation.setRejectReason(note);
            donation.setStatus(DonationStatus.REJECTED);
            donation.setConfirmedAt(LocalDateTime.now());
            donation.setUpdatedAt(LocalDateTime.now());
            Donation saved = donationRepository.save(donation);
            notificationService.broadcastDashboardUpdate();
            sendRejectionNotifications(saved, note.isEmpty() ? "Invalid transaction details" : note);
            return toResponse(saved);
        } else { // DonationStatus.FAILED (Cancelled)
            donation.setRejectReason(null);
            donation.setStatus(DonationStatus.SUCCESSFUL);
            donation.setConfirmedBy(currentUser);
            donation.setConfirmedAt(LocalDateTime.now());
            donation.setUpdatedAt(LocalDateTime.now());
            Donation saved = donationRepository.save(donation);
            publisher.publishDonationConfirmedEvents(saved);
            notificationService.broadcastDashboardUpdate();
            return toResponse(saved);
        }
    }

    private void sendRejectionNotifications(Donation savedDonation, String reason) {
        String amountStr = formatVnd(savedDonation.getAmount());
        String campaignTitle = savedDonation.getCampaign().getTitle();
        String linkUrl = "/campaigns/" + savedDonation.getCampaign().getId();

        // Notify the donor
        if (savedDonation.getUser() != null) {
            sendNotification(
                savedDonation.getUser(),
                "Donation Rejected",
                String.format("Your donation of %s VND for campaign '%s' was rejected by the campaign admin. Reason: %s", amountStr, campaignTitle, reason),
                linkUrl + "?rejectedDonationId=" + savedDonation.getId()
            );
        }
    }

    private void sendPendingApprovalNotification(Donation savedDonation) {
        String amountStr = formatVnd(savedDonation.getAmount());
        String donorName = savedDonation.isAnonymous() ? "Anonymous" : (savedDonation.getUser() != null ? savedDonation.getUser().getFullName() : "Anonymous");
        String campaignTitle = savedDonation.getCampaign().getTitle();
        String linkUrl = "/campaigns/" + savedDonation.getCampaign().getId() + "/approvals";
        String message = String.format("Donor '%s' has submitted a manual donation of %s VND for your campaign '%s' and is pending your approval.", donorName, amountStr, campaignTitle);

        // Notify campaign creator
        if (savedDonation.getCampaign().getUser() != null) {
            sendNotification(
                savedDonation.getCampaign().getUser(),
                "New Pending Donation",
                message,
                linkUrl
            );
        }

        // Also notify all CAMPAIGN_ADMIN members (avoiding double-notifying the creator)
        Long creatorId = savedDonation.getCampaign().getUser() != null ? savedDonation.getCampaign().getUser().getId() : null;
        java.util.List<com.mgmtp.gives.entity.User> campaignAdmins = campaignMemberRepository.findUsersByCampaignIdAndRole(
                savedDonation.getCampaign().getId(), com.mgmtp.gives.enums.CampaignMemberRole.CAMPAIGN_ADMIN);
        for (com.mgmtp.gives.entity.User admin : campaignAdmins) {
            if (!admin.getId().equals(creatorId)) {
                sendNotification(admin, "New Pending Donation", message, linkUrl);
            }
        }
    }

    private String formatVnd(Long amount) {
        if (amount == null) return "0";
        return java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMANY).format(amount);
    }

    private void sendNotification(User recipientUser, String title, String message, String linkUrl) {
        try {
            com.mgmtp.gives.dto.notification.NotificationRecipient recipient =
                    new com.mgmtp.gives.dto.notification.NotificationRecipient(recipientUser.getId(), recipientUser.getEmail());
            com.mgmtp.gives.dto.notification.CreateNotificationCommand command =
                    com.mgmtp.gives.dto.notification.CreateNotificationCommand.builder()
                            .recipients(java.util.Set.of(recipient))
                            .type(com.mgmtp.gives.enums.NotificationType.DONATION)
                            .title(title)
                            .message(message)
                            .linkUrl(linkUrl)
                            .build();
            notificationService.createNotification(command);
        } catch (Exception e) {
            log.error("Failed to send notification to user: {}", recipientUser.getEmail(), e);
        }
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

    @Override
    @Transactional(readOnly = true)
    public List<DonationResponse> getCampaignDonationsForAdmin(Long campaignId, User currentUser) {
        if (!campaignMemberService.canManageCampaign(campaignId, currentUser)) {
            throw new AppException(ErrorCode.UNAUTHORIZED_CAMPAIGN_UPDATE,
                    "Only Campaign Managers or global ADMIN can view all donations.");
        }
        List<Donation> donations = donationRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
        return donations.stream()
                .filter(d -> d.getType() == DonationType.MONEY && d.getOrderCode() == null)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DonationResponse submitManualProof(Long donationId, String proofUrl, User user) {
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new AppException(ErrorCode.DONATE_NOT_FOUND, "Donation not found."));

        if (!donation.getUser().getId().equals(user.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "You do not own this donation record.");
        }

        if (donation.getType() != DonationType.MONEY || donation.getOrderCode() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "This donation is not a manual QR bank transfer.");
        }

        if (donation.getStatus() != DonationStatus.PENDING) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Only pending donations can have proof submitted.");
        }

        donation.setTransactionProofUrl(proofUrl);
        Donation saved = donationRepository.save(donation);
        
        // Refresh dashboard to display the newly uploaded receipt proof image in real time
        notificationService.broadcastDashboardUpdate();
        
        return toResponse(saved);
    }

}
