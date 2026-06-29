package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {

    @Query("""
        SELECT new com.mgmtp.gives.dto.notification.NotificationRecipient(
            c.user.id,
            c.user.email
        )
        FROM Campaign c
        WHERE c.id = :campaignId
        """)
    Optional<NotificationRecipient> findOwnerRecipientByCampaignId(@Param("campaignId") Long campaignId);
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    java.util.List<Campaign> findByStatusAndUpdatedAtBefore(CampaignStatus status, java.time.LocalDateTime dateTime);

    java.util.List<Campaign> findByStatusAndStartDateBetween(CampaignStatus status, java.time.LocalDateTime start,
            java.time.LocalDateTime end);
}
