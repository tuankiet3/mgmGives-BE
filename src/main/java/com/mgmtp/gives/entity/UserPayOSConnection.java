package com.mgmtp.gives.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_payos_connections")
public class UserPayOSConnection extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "checksum_key", nullable = false)
    private String checksumKey;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
