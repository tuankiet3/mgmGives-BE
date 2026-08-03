package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.MediaContext;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaign_medias")
public class CampaignMedia extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne
    @JoinColumn(name = "meeting_id")
    private CampaignMeeting meeting;

    @ManyToOne
    @JoinColumn(name = "announcement_id")
    private Announcement announcement;

    @ManyToOne
    @JoinColumn(name = "spending_id")
    private CampaignSpending spending;

    private String url;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @Column(name = "is_cover", nullable = false)
    private boolean isCover = false;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "context")
    private MediaContext context = MediaContext.CAMPAIGN;
}
