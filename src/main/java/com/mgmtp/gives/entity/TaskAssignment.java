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
@Table(name = "task_assignments")
public class TaskAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private CampaignTask task;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}
