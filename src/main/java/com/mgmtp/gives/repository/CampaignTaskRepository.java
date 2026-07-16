package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampaignTaskRepository extends JpaRepository<CampaignTask, Long>, JpaSpecificationExecutor<CampaignTask> {

    @Query("""
            select coalesce(max(task.position), 0)
            from CampaignTask task
            where task.campaign.id = :campaignId
              and task.status = :status
              and task.isArchived = false
              and task.deletedAt is null
            """)
    long findMaxActivePositionByCampaignIdAndStatus(
            @Param("campaignId") Long campaignId,
            @Param("status") TaskStatus status);

    @Query("""
            SELECT DISTINCT t
            FROM CampaignTask t
            LEFT JOIN FETCH t.assignments a
            LEFT JOIN FETCH a.user
            WHERE t.campaign.id = :campaignId
              AND t.deletedAt IS NULL
              AND t.isArchived = false
            """)
    List<CampaignTask> findActiveTasksWithAssignments(@Param("campaignId") Long campaignId);
}
