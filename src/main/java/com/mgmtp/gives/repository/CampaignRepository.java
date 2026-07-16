package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import com.mgmtp.gives.enums.DonationMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    @Query("SELECT new com.mgmtp.gives.dto.notification.NotificationRecipient(c.user.id, c.user.email) FROM Campaign c WHERE c.id = :campaignId")
    Optional<NotificationRecipient> findOwnerRecipientByCampaignId(@Param("campaignId") Long campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Campaign c WHERE c.id = :campaignId")
    Optional<Campaign> findByIdForUpdate(@Param("campaignId") Long campaignId);

    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    java.util.List<Campaign> findByStatusAndUpdatedAtBefore(CampaignStatus status, java.time.LocalDateTime dateTime);

    java.util.List<Campaign> findByStatusAndStartDateBetween(CampaignStatus status, java.time.LocalDateTime start,
            java.time.LocalDateTime end);

    java.util.List<Campaign> findByStatusAndEndDateBefore(CampaignStatus status, java.time.LocalDateTime dateTime);

    @Query(
            value = """
                SELECT *
                FROM campaigns c
                WHERE c.status = CAST('APPROVED' AS campaign_status)
                  AND c.start_date <= :now
                """,
            nativeQuery = true
    )
    List<Campaign> findApprovedCampaignsToStart(@Param("now") LocalDateTime now);

    @Query(
            value = """
                SELECT *
                FROM campaigns c
                WHERE c.status = CAST('IN_PROGRESS' AS campaign_status)
                  AND c.end_date <= :now
                """,
            nativeQuery = true
    )
    List<Campaign> findInProgressCampaignsToComplete(@Param("now") LocalDateTime now);

    long countByStatus(CampaignStatus status);
    long countByStatusIn(Collection<CampaignStatus> statuses);
    long countByStatusNot(CampaignStatus status);

    boolean existsByUserIdAndStatusInAndDonationMethodIn(
            Long userId,
            Collection<CampaignStatus> statuses,
            Collection<DonationMethod> donationMethods
    );

    @Query("SELECT COUNT(c) FROM Campaign c JOIN c.categories cat WHERE cat.id = :categoryId")
    long countCampaignsByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT COUNT(c) FROM Campaign c JOIN c.categories cat WHERE cat.id = :categoryId AND size(c.categories) = 1")
    long countCampaignsWhereCategoryIsOnlyOne(@Param("categoryId") Long categoryId);
}
