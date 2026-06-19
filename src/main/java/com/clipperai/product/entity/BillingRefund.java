package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "billing_refunds",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "stripe_refund_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_invoice_id")
    private BillingInvoice billingInvoice;

    @Column(name = "stripe_refund_id", nullable = false, unique = true, length = 255)
    private String stripeRefundId;

    @Column(name = "stripe_charge_id", length = 255)
    private String stripeChargeId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "amount_refunded_cents", nullable = false)
    private Integer amountRefundedCents;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(name = "refund_type", nullable = false, length = 50)
    private String refundType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (currency == null || currency.isBlank()) currency = "usd";
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}