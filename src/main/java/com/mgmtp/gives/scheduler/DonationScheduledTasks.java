package com.mgmtp.gives.scheduler;

import com.mgmtp.gives.entity.Donation;
import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import com.mgmtp.gives.repository.DonationRepository;
import com.mgmtp.gives.service.DonationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DonationScheduledTasks {

    private final DonationRepository donationRepository;
    private final DonationService donationService;
    private final PayOS payOS;

    /**
     * Periodically check status of PENDING PayOS donations every 2 minutes.
     */
    @Scheduled(fixedDelay = 120000)
    public void verifyPendingPayOSDonations() {
        log.info("Starting background check for PENDING PayOS donations...");
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<Donation> pendingDonations = donationRepository.findByStatusAndTypeAndTransactionIdIsNotNullAndCreatedAtAfter(
                DonationStatus.PENDING,
                DonationType.MONEY,
                since
        );

        if (pendingDonations.isEmpty()) {
            log.info("No PENDING PayOS donations found.");
            return;
        }

        log.info("Found {} PENDING PayOS donations to verify.", pendingDonations.size());
        for (Donation donation : pendingDonations) {
            try {
                String paymentLinkId = donation.getTransactionId();
                PaymentLink paymentLink = payOS.paymentRequests().get(paymentLinkId);
                if (paymentLink == null) {
                    continue;
                }

                PaymentLinkStatus status = paymentLink.getStatus();
                log.info("Donation ID {}: PayOS status is {}", donation.getId(), status);

                if (status == PaymentLinkStatus.PAID) {
                    log.info("Donation ID {} has been PAID. Actively confirming.", donation.getId());
                    donationService.confirmPayOSDonationByPaymentLinkId(paymentLinkId);
                } else if (status == PaymentLinkStatus.CANCELLED || 
                           status == PaymentLinkStatus.EXPIRED || 
                           status == PaymentLinkStatus.FAILED) {
                    log.info("Donation ID {} is in terminal state {}. Updating status to FAILED in DB.", donation.getId(), status);
                    donation.setStatus(DonationStatus.FAILED);
                    donation.setUpdatedAt(LocalDateTime.now());
                    donationRepository.save(donation);
                }
            } catch (Exception e) {
                log.error("Failed to background-verify donation ID: {}", donation.getId(), e);
            }
        }
        log.info("Completed background check for PENDING PayOS donations.");
    }
}
