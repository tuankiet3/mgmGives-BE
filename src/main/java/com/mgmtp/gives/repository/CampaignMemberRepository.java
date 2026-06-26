package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampaignMemberRepository extends JpaRepository<CampaignMember, Long> {
    Optional<CampaignMember> findByCampaignIdAndUserId(Long campaignId, Long userId);

    long deleteByCampaignIdAndUserId(Long campaignId, Long userId);

    boolean existsByCampaignIdAndUserId(Long campaignId, Long userId);
}
