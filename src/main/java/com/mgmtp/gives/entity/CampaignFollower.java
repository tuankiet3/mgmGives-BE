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
@Table(
        name = "campaign_followers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "user_id"})
)
public class CampaignFollower {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "followed_at")
    private LocalDateTime followedAt;

    @PrePersist
    void prePersist() {
        if (followedAt == null) followedAt = LocalDateTime.now();
    }
}