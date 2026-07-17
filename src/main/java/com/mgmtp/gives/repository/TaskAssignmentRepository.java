package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.TaskAssignment;
import com.mgmtp.gives.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    List<TaskAssignment> findByTaskId(Long taskId);

    boolean existsByTaskIdAndUserId(Long taskId, Long userId);

    @Query("""
                    SELECT COUNT(ta) > 0
                    FROM TaskAssignment ta
                    WHERE ta.user.id = :userId AND ta.task.campaign.id = :campaignId AND ta.task.status <> :excludedStatus
                    """)
    boolean existsByUserIdAndCampaignIdAndTaskStatusNot(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("excludedStatus") TaskStatus excludedStatus);

    @Query("""
                    SELECT ta.user.id, COUNT(ta)
                    FROM TaskAssignment ta
                    WHERE ta.user.id IN :userIds AND ta.task.campaign.id = :campaignId AND ta.task.status <> :excludedStatus
                    GROUP BY ta.user.id
                    """)
    List<Object[]> countByUserIdsAndCampaignIdAndTaskStatusNot(
            @Param("userIds") List<Long> userIds,
            @Param("campaignId") Long campaignId,
            @Param("excludedStatus") TaskStatus excludedStatus);

    @Modifying
    @Query("""
                    DELETE FROM TaskAssignment ta
                    WHERE ta.user.id = :userId AND ta.task.campaign.id = :campaignId AND ta.task.status <> :excludedStatus
                    """)
    void deleteByUserIdAndCampaignIdAndTaskStatusNot(
            @Param("userId") Long userId,
            @Param("campaignId") Long campaignId,
            @Param("excludedStatus") TaskStatus excludedStatus);
}
