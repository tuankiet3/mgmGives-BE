package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.AnnouncementReply;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementReplyRepository extends JpaRepository<AnnouncementReply, Long> {

    Optional<AnnouncementReply> findByIdAndAnnouncementId(Long id, Long announcementId);

    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            WHERE r.id = :replyId AND r.announcement.id = :announcementId
            """)
    Optional<AnnouncementReply> findByIdAndAnnouncementIdWithUser(
            @Param("replyId") Long replyId,
            @Param("announcementId") Long announcementId
    );

    // Native CTE projection: side 0 is newer than the cursor, side 1 is older.
    interface ReplyContextCandidate {
        Long getId();
        Integer getSide();
    }

    // Resolve IDs first so PostgreSQL can use the active-replies partial index before JPA fetch-joins details.
    @Query(value = """
            WITH newer AS (
                SELECT r.id, 0 AS side
                FROM announcement_replies r
                WHERE r.announcement_id = :announcementId
                  AND r.deleted_at IS NULL
                  AND r.id > :cursor
                ORDER BY r.id ASC
                LIMIT :newerLimit
            ),
            older AS (
                SELECT r.id, 1 AS side
                FROM announcement_replies r
                WHERE r.announcement_id = :announcementId
                  AND r.deleted_at IS NULL
                  AND r.id < :cursor
                ORDER BY r.id DESC
                LIMIT :olderLimit
            )
            SELECT id, side FROM newer
            UNION ALL
            SELECT id, side FROM older
            """, nativeQuery = true)
    List<ReplyContextCandidate> findActiveReplyIdsAroundCursor(
            @Param("announcementId") Long announcementId,
            @Param("cursor") Long cursor,
            @Param("newerLimit") int newerLimit,
            @Param("olderLimit") int olderLimit
    );

    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            LEFT JOIN FETCH r.inReplyTo inReplyTo
            LEFT JOIN FETCH inReplyTo.user
            WHERE r.announcement.id = :announcementId
              AND r.deletedAt IS NULL
              AND r.id IN :ids
            """)
    List<AnnouncementReply> findActiveRepliesWithDetailsByAnnouncementIdAndIdIn(
            @Param("announcementId") Long announcementId,
            @Param("ids") Collection<Long> ids
    );

    // 1. ASCENDING (Oldest first)
    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            LEFT JOIN FETCH r.inReplyTo inReplyTo
            LEFT JOIN FETCH inReplyTo.user
            WHERE r.announcement.id = :announcementId AND r.deletedAt IS NULL
            ORDER BY r.id ASC
            """)
    List<AnnouncementReply> findRepliesAscendingFirstPage(
            @Param("announcementId") Long announcementId,
            Pageable pageable
    );

    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            LEFT JOIN FETCH r.inReplyTo inReplyTo
            LEFT JOIN FETCH inReplyTo.user
            WHERE r.announcement.id = :announcementId AND r.id > :cursor AND r.deletedAt IS NULL
            ORDER BY r.id ASC
            """)
    List<AnnouncementReply> findRepliesAscendingWithCursor(
            @Param("announcementId") Long announcementId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    // 2. DESCENDING (Newest first)
    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            LEFT JOIN FETCH r.inReplyTo inReplyTo
            LEFT JOIN FETCH inReplyTo.user
            WHERE r.announcement.id = :announcementId AND r.deletedAt IS NULL
            ORDER BY r.id DESC
            """)
    List<AnnouncementReply> findRepliesDescendingFirstPage(
            @Param("announcementId") Long announcementId,
            Pageable pageable
    );

    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            LEFT JOIN FETCH r.inReplyTo inReplyTo
            LEFT JOIN FETCH inReplyTo.user
            WHERE r.announcement.id = :announcementId AND r.id < :cursor AND r.deletedAt IS NULL
            ORDER BY r.id DESC
            """)
    List<AnnouncementReply> findRepliesDescendingWithCursor(
            @Param("announcementId") Long announcementId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
