package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.CampaignMember;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.enums.CampaignMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CampaignMemberRepository extends JpaRepository<CampaignMember, Long> {
    List<CampaignMember> findByCampaignId(Long campaignId);

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

    long countByCampaignIdAndRoleInCampaign(Long campaignId, CampaignMemberRole role);

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

}
