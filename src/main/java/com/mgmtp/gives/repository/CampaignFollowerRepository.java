package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.CampaignFollower;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
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
public interface CampaignFollowerRepository extends JpaRepository<CampaignFollower, Long> {
    boolean existsByCampaignIdAndUserId(Long campaignId, Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO campaign_followers (campaign_id, user_id, followed_at)
            VALUES (:campaignId, :userId, CURRENT_TIMESTAMP)
            ON CONFLICT (campaign_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(
            @Param("campaignId") Long campaignId,
            @Param("userId") Long userId
    );

    @Query(
            value = """
                SELECT cf
                FROM CampaignFollower cf
                JOIN FETCH cf.campaign c
                LEFT JOIN FETCH c.medias m
                WHERE cf.user.id = :userId
                """,
            countQuery = """
                SELECT COUNT(cf)
                FROM CampaignFollower cf
                WHERE cf.user.id = :userId
                """
    )
    Page<CampaignFollower> findAllByUserIdWithCampaign(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query(
            value = """
                SELECT cf
                FROM CampaignFollower cf
                JOIN FETCH cf.campaign c
                LEFT JOIN FETCH c.medias m
                WHERE cf.user.id = :userId
                  AND (:keyword = '' OR LOWER(c.title) LIKE CONCAT('%', LOWER(:keyword), '%'))
                  AND (CAST(:status AS string) IS NULL OR c.status = :status)
                  AND (CAST(:priority AS string) IS NULL OR c.priority = :priority)
                  AND (:hasCategories = false OR (
                      SELECT COUNT(DISTINCT cat.id)
                      FROM c.categories cat
                      WHERE cat.id IN :categoryIds
                  ) = :categoryCount)
                """,
            countQuery = """
                SELECT COUNT(cf)
                FROM CampaignFollower cf
                JOIN cf.campaign c
                WHERE cf.user.id = :userId
                  AND (:keyword = '' OR LOWER(c.title) LIKE CONCAT('%', LOWER(:keyword), '%'))
                  AND (CAST(:status AS string) IS NULL OR c.status = :status)
                  AND (CAST(:priority AS string) IS NULL OR c.priority = :priority)
                  AND (:hasCategories = false OR (
                      SELECT COUNT(DISTINCT cat.id)
                      FROM c.categories cat
                      WHERE cat.id IN :categoryIds
                  ) = :categoryCount)
                """
    )
    Page<CampaignFollower> findAllByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("status") CampaignStatus status,
            @Param("priority") CampaignPriority priority,
            @Param("hasCategories") boolean hasCategories,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("categoryCount") long categoryCount,
            Pageable pageable
    );

    List<CampaignFollower> findAllByCampaignId(Long campaignId);

    @Query("""
        SELECT new com.mgmtp.gives.dto.notification.NotificationRecipient(
            cf.user.id,
            cf.user.email
        )
        FROM CampaignFollower cf
        WHERE cf.campaign.id = :campaignId
        """)
    List<NotificationRecipient> findFollowerRecipientsByCampaignId(@Param("campaignId") Long campaignId);

    long deleteByCampaignIdAndUserId(Long campaignId, Long userId);

    @Query("""
            SELECT cf.user
            FROM CampaignFollower cf
            WHERE cf.campaign.id = :campaignId
            """)
    List<User> findFollowerUsersByCampaignId(@Param("campaignId") Long campaignId);

    long countByUserId(Long userId);
}
