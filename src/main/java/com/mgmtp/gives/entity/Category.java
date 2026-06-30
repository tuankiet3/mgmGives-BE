package com.mgmtp.gives.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @ManyToMany(mappedBy = "categories")
    private Set<Campaign> campaigns = new HashSet<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
