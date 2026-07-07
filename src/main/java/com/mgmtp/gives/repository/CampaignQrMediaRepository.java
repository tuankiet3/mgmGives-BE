package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignQrMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignQrMediaRepository extends JpaRepository<CampaignQrMedia, Long> {
    Optional<CampaignQrMedia> findByCampaignId(Long campaignId);

    void deleteByCampaignId(Long campaignId);

    java.util.List<CampaignQrMedia> findByCampaignIdIn(java.util.List<Long> campaignIds);
}
