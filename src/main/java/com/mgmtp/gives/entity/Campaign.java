package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.CampaignPriority;
import com.mgmtp.gives.enums.CampaignStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "campaigns")
public class Campaign extends BaseEntity {

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    private Long target;

    @Enumerated(EnumType.STRING)
    private CampaignPriority priority;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "campaign_categories",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

//    @OneToMany(mappedBy = "campaign")
//    private List<CampaignMedia> media = new ArrayList<>();
//
//    @OneToMany(mappedBy = "campaign")
//    private List<CampaignTask> tasks = new ArrayList<>();
//
//    @OneToMany(mappedBy = "campaign")
//    private List<CampaignMember> members = new ArrayList<>();
//
//    @OneToMany(mappedBy = "campaign")
//    private List<Announcement> announcements = new ArrayList<>();


}
