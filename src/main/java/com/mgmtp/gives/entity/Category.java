package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.CategoryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Builder.Default
    @Column(name = "status", nullable = false, columnDefinition = "campaign_category_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CategoryStatus status = CategoryStatus.PENDING;
}
