package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.CampaignMemberRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaign_members")
public class CampaignMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role_in_campaign", columnDefinition = "campaign_member_role")
    private CampaignMemberRole roleInCampaign;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "unjoin_requested_at")
    private LocalDateTime unjoinRequestedAt;

    /**
     * Member-level opt-out from the campaign's public roster. Only consulted when the
     * campaign's memberListVisibility is PUBLIC; admins and fellow members always see
     * the full list regardless of this flag.
     */
    @Builder.Default
    @Column(name = "hidden_from_public_list", nullable = false)
    private boolean hiddenFromPublicList = false;
}
