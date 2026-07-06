package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.Announcement;
import com.mgmtp.gives.enums.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByStatusOrderByPublishedAtDesc(AnnouncementStatus status);

    Page<Announcement> findByCampaignId(Long campaignId, Pageable pageable);
}
