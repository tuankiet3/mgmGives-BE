package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignMediaRepository extends JpaRepository<CampaignMedia, Long> {

    List<CampaignMedia> findByCampaignId(Long campaignId);

    List<CampaignMedia> findByCampaignIdAndDeletedAtIsNull(Long campaignId);

    Optional<CampaignMedia> findByUrl(String url);

    List<CampaignMedia> findByDeletedAtNotNullAndDeletedAtBefore(LocalDateTime cutoff);
}
