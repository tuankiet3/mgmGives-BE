package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.campaign.DonorNotificationInfo;
import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.mgmtp.gives.enums.DonationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.mgmtp.gives.enums.DonationType;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long>, JpaSpecificationExecutor<Donation> {
    List<Donation> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Donation> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

    List<Donation> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.user.id = :userId AND d.status = :status AND d.type = :type")
    long sumAmountByUserIdAndStatusAndType(
            @Param("userId") Long userId,
            @Param("status") DonationStatus status,
            @Param("type") DonationType type
    );

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status <> :failedStatus")
    Long sumAmountByCampaignIdAndStatusNotFailed(@Param("campaignId") Long campaignId, @Param("failedStatus") DonationStatus failedStatus);

    @Query("SELECT d FROM Donation d WHERE d.campaign.id = :campaignId AND d.status NOT IN :statuses ORDER BY d.createdAt DESC")
    List<Donation> findByCampaignIdAndStatusNotInOrderByCreatedAtDesc(
            @Param("campaignId") Long campaignId,
            @Param("statuses") Collection<DonationStatus> statuses
    );

    @Query("SELECT COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long countDistinctDonorsByCampaignIdAndStatusSuccessful(@Param("campaignId") Long campaignId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long sumConfirmedAmountByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL'")
    long countDistinctDonorsByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT d.campaign.id, COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.campaign.id IN :campaignIds AND d.status = 'SUCCESSFUL' GROUP BY d.campaign.id")
    List<Object[]> sumConfirmedAmountByCampaignIds(@Param("campaignIds") List<Long> campaignIds);

    @Query("SELECT d.campaign.id, COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id IN :campaignIds AND d.status = 'SUCCESSFUL' GROUP BY d.campaign.id")
    List<Object[]> countDistinctDonorsByCampaignIds(@Param("campaignIds") List<Long> campaignIds);

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

    @Query("""
            SELECT DISTINCT new com.mgmtp.gives.dto.notification.NotificationRecipient(
                d.user.id,
                d.user.email
            )
            FROM Donation d
            WHERE d.campaign.id = :campaignId AND d.status = 'SUCCESSFUL' AND d.user IS NOT NULL
            """)
    List<NotificationRecipient> findDonorRecipientsByCampaignId(@Param("campaignId") Long campaignId);

    List<Donation> findByCampaignIdAndStatus(Long campaignId, DonationStatus status);

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

    @Query("""
        SELECT COALESCE(SUM(d.amount), 0)
        FROM Donation d
        WHERE d.status = :status
          AND d.type = :type
          AND d.createdAt >= :startOfYear
          AND d.createdAt <= :endOfYear
        """)
    long sumConfirmedDonationsByYear(
            @Param("status") com.mgmtp.gives.enums.DonationStatus status,
            @Param("type") com.mgmtp.gives.enums.DonationType type,
            @Param("startOfYear") java.time.LocalDateTime startOfYear,
            @Param("endOfYear") java.time.LocalDateTime endOfYear
    );

    @Query("""
        SELECT d
        FROM Donation d
        WHERE d.campaign.id IN (
            SELECT cf.campaign.id FROM CampaignFollower cf WHERE cf.user.id = :userId
        ) OR d.campaign.id IN (
            SELECT cm.campaign.id FROM CampaignMember cm WHERE cm.user.id = :userId
        ) OR d.user.id = :userId
        ORDER BY d.createdAt DESC
        """)
    List<Donation> findDonationsForFollowedAndJoinedCampaigns(
            @Param("userId") Long userId, 
            org.springframework.data.domain.Pageable pageable
    );

    Optional<Donation> findByIdempotencyKey(String idempotencyKey);
}
