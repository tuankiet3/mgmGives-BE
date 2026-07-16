package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.TaskStatus;
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
@Table(name = "campaign_tasks")
public class CampaignTask extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "task_status")
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "position")
    private long position;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "is_archived")
    private boolean isArchived = false;

    @Builder.Default
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<TaskAssignment> assignments = new java.util.HashSet<>();

    @Builder.Default
    @ManyToMany
    @JoinTable(name = "task_labels", joinColumns = @JoinColumn(name = "task_id"), inverseJoinColumns = @JoinColumn(name = "label_id"))
    private java.util.Set<CampaignTaskLabel> labels = new java.util.HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<TaskAttachment> attachments = new java.util.HashSet<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}
