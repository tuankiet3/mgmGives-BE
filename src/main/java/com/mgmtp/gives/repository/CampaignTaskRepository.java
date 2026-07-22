package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface CampaignTaskRepository
                extends JpaRepository<CampaignTask, Long>, JpaSpecificationExecutor<CampaignTask> {

        @Query(value = "select nextval('campaign_task_position_seq')", nativeQuery = true)
        long nextPosition();

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

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("""
                        update CampaignTask task
                           set task.position = task.position + 1,
                               task.version = task.version + 1
                         where task.campaign.id = :campaignId
                           and task.status = :status
                           and task.position >= :position
                           and task.id <> :excludedTaskId
                           and task.isArchived = false
                           and task.deletedAt is null
                        """)
        int shiftPositionsAfter(
                        @Param("campaignId") Long campaignId,
                        @Param("status") TaskStatus status,
                        @Param("excludedTaskId") Long excludedTaskId,
                        @Param("position") Long position);

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

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        update campaign_tasks
                           set status = cast(:status as task_status),
                               position = COALESCE(:position, nextval('campaign_task_position_seq')),
                               updated_at = :updatedAt,
                               version = version + 1
                         where id = :taskId
                           and version = :expectedVersion
                        """, nativeQuery = true)
        int moveIfVersionMatches(
                        @Param("taskId") Long taskId,
                        @Param("status") String status,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("position") Long position,
                        @Param("updatedAt") LocalDateTime updatedAt);

}
