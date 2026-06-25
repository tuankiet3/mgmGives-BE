package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.CampaignMemberRole;
import com.mgmtp.gives.enums.CampaignMemberStatus;
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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "campaign_member_status")
    private CampaignMemberStatus status;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
