package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_grants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_period_id")
    private UsagePeriod usagePeriod;

    @Column(name = "grant_type", nullable = false, length = 50)
    private String grantType;

    @Column(name = "source_videos_granted", nullable = false)
    private Integer sourceVideosGranted;

    @Column(name = "source_videos_used", nullable = false)
    private Integer sourceVideosUsed;

    @Column(name = "amount_paid_cents")
    private Integer amountPaidCents;

    @Column(name = "stripe_payment_intent_id", unique = true, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_invoice_id", length = 255)
    private String stripeInvoiceId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "stripe_refund_id", length = 255)
    private String stripeRefundId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (sourceVideosGranted == null) sourceVideosGranted = 0;
        if (sourceVideosUsed == null) sourceVideosUsed = 0;
        if (status == null || status.isBlank()) status = "pending";
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}