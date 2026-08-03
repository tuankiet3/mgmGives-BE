package com.mgmtp.gives.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementCounterReconciliationJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 3 * * *") // Runs daily at 3 AM
    @Transactional
    public void reconcileCounters() {
        log.info("Starting Announcement Counter Reconciliation Job...");

        // 1. Reconcile Likes Count (Aggregate matches)
        int updatedLikes = jdbcTemplate.update("""
            UPDATE announcements a
            SET likes_count = sub.cnt
            FROM (
                SELECT announcement_id, COUNT(*) as cnt
                FROM announcement_likes
                GROUP BY announcement_id
            ) sub
            WHERE a.id = sub.announcement_id AND a.likes_count <> sub.cnt
            """);

        // Zero out likes count where no likes exist
        int zeroedLikes = jdbcTemplate.update("""
            UPDATE announcements a
            SET likes_count = 0
            WHERE a.likes_count <> 0 
              AND NOT EXISTS (SELECT 1 FROM announcement_likes l WHERE l.announcement_id = a.id)
            """);

        // 2. Reconcile Replies Count (Aggregate active matches)
        int updatedReplies = jdbcTemplate.update("""
            UPDATE announcements a
            SET replies_count = sub.cnt
            FROM (
                SELECT announcement_id, COUNT(*) as cnt
                FROM announcement_replies
                WHERE deleted_at IS NULL
                GROUP BY announcement_id
            ) sub
            WHERE a.id = sub.announcement_id AND a.replies_count <> sub.cnt
            """);

        // Zero out replies count where no active replies exist
        int zeroedReplies = jdbcTemplate.update("""
            UPDATE announcements a
            SET replies_count = 0
            WHERE a.replies_count <> 0 
              AND NOT EXISTS (SELECT 1 FROM announcement_replies r WHERE r.announcement_id = a.id AND r.deleted_at IS NULL)
            """);

        int totalDriftLikes = updatedLikes + zeroedLikes;
        int totalDriftReplies = updatedReplies + zeroedReplies;

        if (totalDriftLikes > 0) {
            log.warn("Reconciled {} announcements with likes_count drift", totalDriftLikes);
        } else {
            log.info("Likes count reconciled. No drift detected.");
        }
        
        if (totalDriftReplies > 0) {
            log.warn("Reconciled {} announcements with replies_count drift", totalDriftReplies);
        } else {
            log.info("Replies count reconciled. No drift detected.");
        }
        
        log.info("Announcement Counter Reconciliation Job completed.");
    }
}
