package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignMemberRepository extends JpaRepository<CampaignMember, Long> {
        List<CampaignMember> findByCampaignId(Long campaignId);
        
        long countByUserIdAndCampaignStatusAndRoleInCampaign(Long userId, CampaignStatus status, CampaignMemberRole role);

        @Query("""
                        SELECT DISTINCT new com.mgmtp.gives.dto.notification.NotificationRecipient(
                            cm.user.id,
                            cm.user.email
                        )
                        FROM CampaignMember cm
                        WHERE cm.campaign.id = :campaignId AND cm.user IS NOT NULL
                        """)
        List<NotificationRecipient> findMemberRecipientsByCampaignId(@Param("campaignId") Long campaignId);

        Optional<CampaignMember> findByCampaignIdAndUserId(Long campaignId, Long userId);

        long deleteByCampaignIdAndUserId(Long campaignId, Long userId);

        boolean existsByCampaignIdAndUserId(Long campaignId, Long userId);

        boolean existsByCampaignIdAndUserIdAndRoleInCampaign(Long campaignId, Long userId, CampaignMemberRole role);

        @Query("SELECT cm.campaign.id FROM CampaignMember cm WHERE cm.user.id = :userId AND cm.roleInCampaign = :role AND cm.campaign.id IN :campaignIds")
        List<Long> findCampaignIdsByUserIdAndRoleAndCampaignIdsIn(
                @Param("userId") Long userId,
                @Param("role") CampaignMemberRole role,
                @Param("campaignIds") List<Long> campaignIds
        );

        long countByCampaignIdAndRoleInCampaign(Long campaignId, CampaignMemberRole role);

        @Query(value = """
                        SELECT cm
                        FROM CampaignMember cm
                        JOIN FETCH cm.campaign c
                        WHERE cm.user.id = :userId
                          AND cm.roleInCampaign = :role
                          AND (:keyword = '' OR LOWER(c.title) LIKE CONCAT('%', LOWER(:keyword), '%'))
                          AND (CAST(:status AS string) IS NULL OR c.status = :status)
                          AND (CAST(:priority AS string) IS NULL OR c.priority = :priority)
                          AND (:hasCategories = false OR (SELECT COUNT(cat) FROM c.categories cat WHERE cat.id IN :categoryIds) = :categoryCount)
                        """, countQuery = """
                        SELECT COUNT(cm)
                        FROM CampaignMember cm
                        JOIN cm.campaign c
                        WHERE cm.user.id = :userId
                          AND cm.roleInCampaign = :role
                          AND (:keyword = '' OR LOWER(c.title) LIKE CONCAT('%', LOWER(:keyword), '%'))
                          AND (CAST(:status AS string) IS NULL OR c.status = :status)
                          AND (CAST(:priority AS string) IS NULL OR c.priority = :priority)
                          AND (:hasCategories = false OR (SELECT COUNT(cat) FROM c.categories cat WHERE cat.id IN :categoryIds) = :categoryCount)
                        """)
        Page<CampaignMember> findAllByUserIdWithFilters(
                        @Param("userId") Long userId,
                        @Param("role") CampaignMemberRole role,
                        @Param("keyword") String keyword,
                        @Param("status") CampaignStatus status,
                        @Param("priority") CampaignPriority priority,
                        @Param("hasCategories") boolean hasCategories,
                        @Param("categoryIds") List<Long> categoryIds,
                        @Param("categoryCount") long categoryCount,
                        Pageable pageable);

        @Query("SELECT cm.campaign.id, COUNT(cm.id) FROM CampaignMember cm WHERE cm.campaign.id IN :campaignIds AND cm.roleInCampaign = :role GROUP BY cm.campaign.id")
        List<Object[]> countByCampaignIdsAndRoleInCampaign(@Param("campaignIds") List<Long> campaignIds,
                        @Param("role") CampaignMemberRole role);

        @Query("""
                        SELECT new com.mgmtp.gives.dto.notification.NotificationRecipient(
                            cm.user.id,
                            cm.user.email
                        )
                        FROM CampaignMember cm
                        WHERE cm.campaign.id = :campaignId AND cm.roleInCampaign = :role
                        """)
        List<NotificationRecipient> findRecipientsByCampaignIdAndRole(
                        @Param("campaignId") Long campaignId,
                        @Param("role") CampaignMemberRole role);

        @Query("""
                        SELECT cm.user
                        FROM CampaignMember cm
                        WHERE cm.campaign.id = :campaignId AND cm.roleInCampaign = :role
                        """)
        List<User> findUsersByCampaignIdAndRole(
                        @Param("campaignId") Long campaignId,
                        @Param("role") CampaignMemberRole role);

        @Query(value = """
                        SELECT cm
                        FROM CampaignMember cm
                        JOIN FETCH cm.user u
                        WHERE cm.campaign.id = :campaignId AND cm.unjoinRequestedAt IS NOT NULL
                        """, countQuery = """
                        SELECT COUNT(cm)
                        FROM CampaignMember cm
                        WHERE cm.campaign.id = :campaignId AND cm.unjoinRequestedAt IS NOT NULL
                        """)
        Page<CampaignMember> findByCampaignIdAndUnjoinRequestedAtIsNotNull(
                        @Param("campaignId") Long campaignId, Pageable pageable);

        @Query("""
                        SELECT cm
                        FROM CampaignMember cm
                        JOIN FETCH cm.user
                        WHERE cm.campaign.id = :campaignId AND cm.roleInCampaign = :role
                        ORDER BY cm.joinedAt ASC
                        """)
        List<CampaignMember> findByCampaignIdAndRoleWithUser(
                        @Param("campaignId") Long campaignId,
                        @Param("role") CampaignMemberRole role);

}
