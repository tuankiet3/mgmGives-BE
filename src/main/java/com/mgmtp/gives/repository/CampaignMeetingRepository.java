package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CampaignMeetingRepository extends JpaRepository<CampaignMeeting, Long> {
    List<CampaignMeeting> findByCampaignIdOrderByStartTimeAsc(Long campaignId);

    List<CampaignMeeting> findByCampaignIdAndStatusOrderByStartTimeAsc(
            Long campaignId,
            CampaignMeetingStatus status
    );

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM CampaignMeeting m
            WHERE m.campaign.id = :campaignId
              AND m.status <> :cancelledStatus
              AND (:excludedMeetingId IS NULL OR m.id <> :excludedMeetingId)
              AND :startTime < m.endTime
              AND :endTime > m.startTime
            """)
    boolean existsOverlappingActiveMeeting(
            @Param("campaignId") Long campaignId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedMeetingId") Long excludedMeetingId,
            @Param("cancelledStatus") CampaignMeetingStatus cancelledStatus
    );
}
