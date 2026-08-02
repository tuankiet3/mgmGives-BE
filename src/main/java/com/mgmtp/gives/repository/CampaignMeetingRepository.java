package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignMeeting;
import com.mgmtp.gives.enums.CampaignMeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
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
      AND m.status IN :statuses
      AND (:excludedMeetingId IS NULL OR m.id <> :excludedMeetingId)
      AND :startTime < m.endTime
      AND :endTime > m.startTime
    """)
    boolean existsOverlappingMeeting(
            @Param("campaignId") Long campaignId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludedMeetingId") Long excludedMeetingId,
            @Param("statuses") Collection<CampaignMeetingStatus> statuses
    );

    @Query("""
            SELECT m
            FROM CampaignMeeting m
            WHERE m.status IN (:status1, :status2)
              AND m.webexMeetingId IS NOT NULL
              AND m.webexMeetingId <> ''
            """)
    List<CampaignMeeting> findWebexMeetingsByStatus(CampaignMeetingStatus status1, CampaignMeetingStatus status2);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE campaign_meetings
            SET status = 'ENDED'::campaign_meeting_status,
                updated_at = :now
            WHERE status IN (
                'UPCOMING'::campaign_meeting_status,
                'IN_PROGRESS'::campaign_meeting_status
            )
              AND meeting_type = 'OFFLINE'
              AND (webex_meeting_id IS NULL OR webex_meeting_id = '')
              AND end_time IS NOT NULL
              AND end_time <= :now
            """, nativeQuery = true)
    int markScheduledMeetingsEnded(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE campaign_meetings
            SET status = 'IN_PROGRESS'::campaign_meeting_status,
                updated_at = :now
            WHERE status = 'UPCOMING'::campaign_meeting_status
              AND meeting_type = 'OFFLINE'
              AND (webex_meeting_id IS NULL OR webex_meeting_id = '')
              AND start_time IS NOT NULL
              AND start_time <= :now
              AND (end_time IS NULL OR end_time > :now)
            """, nativeQuery = true)
    int markScheduledMeetingsInProgress(@Param("now") LocalDateTime now);
}
