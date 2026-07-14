package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByOrderByPublishedAtDesc();

    Page<Announcement> findByOrderByPublishedAtDesc(Pageable pageable);

    Page<Announcement> findByCampaignId(Long campaignId, Pageable pageable);

    Optional<Announcement> findByIdAndCampaignId(Long id, Long campaignId);

    List<Announcement> findByCampaignIdOrderByPublishedAtAsc(Long campaignId);

    List<Announcement> findByCreatedByIdOrderByPublishedAtDesc(
            Long createdById, 
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Announcement a SET a.likesCount = a.likesCount + 1 WHERE a.id = :id")
    void incrementLikesCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Announcement a SET a.likesCount = a.likesCount - 1 WHERE a.id = :id")
    void decrementLikesCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Announcement a SET a.repliesCount = a.repliesCount + 1 WHERE a.id = :id")
    void incrementRepliesCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Announcement a SET a.repliesCount = a.repliesCount - 1 WHERE a.id = :id")
    void decrementRepliesCount(@Param("id") Long id);
}
