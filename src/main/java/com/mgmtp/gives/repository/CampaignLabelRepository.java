package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignTaskLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignLabelRepository extends JpaRepository<CampaignTaskLabel, Long> {
    List<CampaignTaskLabel> findByCampaignId(Long campaignId);

    List<CampaignTaskLabel> findByIdInAndCampaignId(List<Long> ids, Long campaignId);
}
