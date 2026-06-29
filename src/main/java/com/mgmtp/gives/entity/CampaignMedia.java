package com.mgmtp.gives.entity;

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

    private String url;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @Column(name = "is_cover", nullable = false)
    private boolean isCover = false;
}
