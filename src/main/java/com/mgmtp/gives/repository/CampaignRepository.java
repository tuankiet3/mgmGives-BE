package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    java.util.List<Campaign> findByStatusAndUpdatedAtBefore(CampaignStatus status, java.time.LocalDateTime dateTime);

    java.util.List<Campaign> findByStatusAndStartDateBetween(CampaignStatus status, java.time.LocalDateTime start,
            java.time.LocalDateTime end);
}
