package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CampaignTaskRepository extends JpaRepository<CampaignTask, Long>, JpaSpecificationExecutor<CampaignTask> {
}
