package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignSpending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampaignSpendingRepository extends JpaRepository<CampaignSpending, Long> {

    @Query("""
            SELECT s FROM CampaignSpending s
            WHERE s.campaign.id = :campaignId AND s.deletedAt IS NULL
            ORDER BY s.spentAt DESC, s.id DESC
            """)
    List<CampaignSpending> findActiveByCampaignId(@Param("campaignId") Long campaignId);

    @Query("""
            SELECT COALESCE(SUM(s.amount), 0) FROM CampaignSpending s
            WHERE s.campaign.id = :campaignId AND s.deletedAt IS NULL
            """)
    long sumAmountByCampaignId(@Param("campaignId") Long campaignId);
}
