package com.mgmtp.gives.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "announcements")
public class Announcement extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;



    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
