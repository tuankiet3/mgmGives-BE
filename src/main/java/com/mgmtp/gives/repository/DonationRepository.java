package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.mgmtp.gives.enums.DonationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface DonationRepository extends JpaRepository<Donation, Long>, JpaSpecificationExecutor<Donation> {
    List<Donation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status <> :failedStatus")
    Long sumAmountByCampaignIdAndStatusNotFailed(@Param("campaignId") Long campaignId, @Param("failedStatus") DonationStatus failedStatus);

    List<Donation> findByCampaignIdAndStatusNotOrderByCreatedAtDesc(Long campaignId, DonationStatus status);

    @Query("SELECT COUNT(DISTINCT d.user.id) FROM Donation d WHERE d.campaign.id = :campaignId AND d.status = 'CONFIRMED'")
    long countDistinctDonorsByCampaignIdAndStatusConfirmed(@Param("campaignId") Long campaignId);
}

