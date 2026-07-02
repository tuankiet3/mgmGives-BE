package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.mgmtp.gives.enums.DonationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long>, JpaSpecificationExecutor<Donation> {
    List<Donation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status <> :failedStatus")
    Long sumAmountByCampaignIdAndStatusNotFailed(@Param("campaignId") Long campaignId, @Param("failedStatus") DonationStatus failedStatus);

    List<Donation> findByCampaignIdAndStatusNotOrderByCreatedAtDesc(Long campaignId, DonationStatus status);

    @Query("SELECT COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long countDistinctDonorsByCampaignIdAndStatusSuccessful(@Param("campaignId") Long campaignId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long sumConfirmedAmountByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long countDistinctDonorsByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long countDistinctDonorsByCampaignIdAndStatusConfirmed(@Param("campaignId") Long campaignId);

    @Query("""
            SELECT new com.mgmtp.gives.dto.campaign.DonorNotificationInfo(
                d.user.id, d.user.email, d.user.fullName, SUM(d.amount)
            )
            FROM Donation d
            WHERE d.campaign.id = :campaignId AND d.status = :status
            GROUP BY d.user.id, d.user.email, d.user.fullName
            """)
    List<DonorNotificationInfo> findDonorNotificationInfoByCampaignId(
            @Param("campaignId") Long campaignId,
            @Param("status") DonationStatus status);

    Optional<Donation> findByTransactionId(String transactionId);

    List<Donation> findByStatusAndTypeAndTransactionIdIsNotNull(com.mgmtp.gives.enums.DonationStatus status, com.mgmtp.gives.enums.DonationType type);

    List<Donation> findByStatusAndTypeAndTransactionIdIsNotNullAndCreatedAtAfter(
            com.mgmtp.gives.enums.DonationStatus status,
            com.mgmtp.gives.enums.DonationType type,
            java.time.LocalDateTime since
    );

    List<Donation> findByStatusAndTypeAndTransactionIdIsNotNullAndCreatedAtBefore(
            com.mgmtp.gives.enums.DonationStatus status,
            com.mgmtp.gives.enums.DonationType type,
            java.time.LocalDateTime limit
    );
}
