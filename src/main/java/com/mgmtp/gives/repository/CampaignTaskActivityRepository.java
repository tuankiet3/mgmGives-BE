package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignTaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignTaskActivityRepository extends JpaRepository<CampaignTaskActivity, Long> {
    @EntityGraph(attributePaths = "actor")
    Page<CampaignTaskActivity> findByTaskId(Long taskId, Pageable pageable);
}
