package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.AnnouncementReply;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementReplyRepository extends JpaRepository<AnnouncementReply, Long> {

    Optional<AnnouncementReply> findByIdAndAnnouncementId(Long id, Long announcementId);

    // 1. ASCENDING (Oldest first)
    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            WHERE r.announcement.id = :announcementId AND r.deletedAt IS NULL
            ORDER BY r.id ASC
            """)
    List<AnnouncementReply> findRepliesAscendingFirstPage(
            @Param("announcementId") Long announcementId,
            Pageable pageable
    );

    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
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
            WHERE r.announcement.id = :announcementId AND r.deletedAt IS NULL
            ORDER BY r.id DESC
            """)
    List<AnnouncementReply> findRepliesDescendingFirstPage(
            @Param("announcementId") Long announcementId,
            Pageable pageable
    );

    @Query("""
            SELECT r FROM AnnouncementReply r JOIN FETCH r.user
            WHERE r.announcement.id = :announcementId AND r.id < :cursor AND r.deletedAt IS NULL
            ORDER BY r.id DESC
            """)
    List<AnnouncementReply> findRepliesDescendingWithCursor(
            @Param("announcementId") Long announcementId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
