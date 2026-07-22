package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.AnnouncementLike;
import com.mgmtp.gives.entity.AnnouncementLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.Set;

@Repository
public interface AnnouncementLikeRepository extends JpaRepository<AnnouncementLike, AnnouncementLikeId> {
    
    /**
     * Bulk-checks if the user has liked a collection of announcements.
     * Used to resolve the N+1 query problem when loading the announcement feed.
     * 
     * @param announcementIds the list of announcement IDs to check
     * @param userId the ID of the authenticated user
     * @return the set of announcement IDs that the user has liked
     */
    @Query("SELECT l.announcement.id FROM AnnouncementLike l WHERE l.announcement.id IN :announcementIds AND l.user.id = :userId")
    Set<Long> findLikedAnnouncementIdsByAnnouncementIdInAndUserId(
            @Param("announcementIds") Collection<Long> announcementIds,
            @Param("userId") Long userId
    );
    
    /**
     * Atomically inserts a like record.
     * Uses PostgreSQL 'ON CONFLICT DO NOTHING' to prevent duplicate like errors.
     * 
     * @return 1 if a new like was successfully registered, 0 if the like already existed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true) // clearAutomatically evicts Hibernate L1 cache to prevent stale entity states
    @Query(value = """
            INSERT INTO announcement_likes (announcement_id, user_id, created_at)
            VALUES (:announcementId, :userId, CURRENT_TIMESTAMP)
            ON CONFLICT (announcement_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(
            @Param("announcementId") Long announcementId,
            @Param("userId") Long userId
    );

    /**
     * Atomically deletes a like record.
     * 
     * @return 1 if the like record was found and deleted, 0 if the like did not exist.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true) // clearAutomatically evicts Hibernate L1 cache to prevent stale entity states
    @Query("DELETE FROM AnnouncementLike l WHERE l.announcement.id = :announcementId AND l.user.id = :userId")
    int deleteLike(
            @Param("announcementId") Long announcementId,
            @Param("userId") Long userId
    );
}
