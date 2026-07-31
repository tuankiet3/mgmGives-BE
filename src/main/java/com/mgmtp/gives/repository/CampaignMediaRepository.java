package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.enums.MediaContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignMediaRepository extends JpaRepository<CampaignMedia, Long> {

    List<CampaignMedia> findByCampaignId(Long campaignId);

    List<CampaignMedia> findByCampaignIdAndDeletedAtIsNull(Long campaignId);

    List<CampaignMedia> findByCampaignIdAndContextAndDeletedAtIsNull(Long campaignId, MediaContext context);

    List<CampaignMedia> findByCampaignIdAndContextNotAndDeletedAtIsNull(Long campaignId, MediaContext context);

    List<CampaignMedia> findByCampaignIdAndMeetingIdAndDeletedAtIsNull(Long campaignId, Long meetingId);

    Optional<CampaignMedia> findByIdAndCampaignIdAndMeetingIdAndDeletedAtIsNull(Long id, Long campaignId, Long meetingId);

    List<CampaignMedia> findBySpendingIdAndDeletedAtIsNull(Long spendingId);

    List<CampaignMedia> findBySpendingIdInAndDeletedAtIsNull(List<Long> spendingIds);

    Optional<CampaignMedia> findByIdAndSpendingIdAndDeletedAtIsNull(Long id, Long spendingId);

    boolean existsByCampaignIdAndDeletedAtIsNullAndIsCoverTrue(Long campaignId);

    Optional<CampaignMedia> findByCampaignIdAndDeletedAtIsNullAndIsCoverTrue(Long campaignId);

    @Query("SELECT m FROM CampaignMedia m WHERE m.campaign.id IN :campaignIds AND m.deletedAt IS NULL AND m.isCover = true")
    List<CampaignMedia> findCoverImagesByCampaignIds(@Param("campaignIds") List<Long> campaignIds);
    Optional<CampaignMedia> findByUrl(String url);

    List<CampaignMedia> findByAnnouncementIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(Long announcementId);

    List<CampaignMedia> findByAnnouncementIdInAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(List<Long> announcementIds);

    List<CampaignMedia> findByDeletedAtNotNullAndDeletedAtBefore(LocalDateTime cutoff);
}
