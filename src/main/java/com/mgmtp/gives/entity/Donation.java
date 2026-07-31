package com.mgmtp.gives.entity;

import com.mgmtp.gives.enums.DonationStatus;
import com.mgmtp.gives.enums.DonationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "donations")
public class Donation extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", columnDefinition = "donation_type")
    private DonationType type;

    private Long amount;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "is_anonymous")
    private boolean isAnonymous;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "donation_status")
    private DonationStatus status;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "transaction_description")
    private String transactionDescription;

    @Column(name = "transaction_proof_url")
    private String transactionProofUrl;

    @Column(name = "order_code")
    private Long orderCode;

    @ManyToOne
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "message", length = 280)
    private String message;

    @Column(name = "is_message_hidden")
    private boolean isMessageHidden;

    @Builder.Default
    @Column(name = "is_amount_hidden")
    private boolean isAmountHidden = true;

    @Column(name = "goods_condition")
    private String goodsCondition;

    @Column(name = "goods_category")
    private String goodsCategory;

    @Column(name = "delivery_method")
    private String deliveryMethod;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

}

