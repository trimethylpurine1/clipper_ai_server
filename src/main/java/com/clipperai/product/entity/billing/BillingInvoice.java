package com.clipperai.product.entity.billing;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.clipperai.product.entity.AppUser;

@Entity
@Table(
        name = "billing_invoices",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "stripe_invoice_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private UserSubscription subscription;

    @Column(name = "stripe_invoice_id", unique = true, length = 255)
    private String stripeInvoiceId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "stripe_charge_id", length = 255)
    private String stripeChargeId;

    @Column(name = "amount_paid_cents", nullable = false)
    private Integer amountPaidCents;

    @Column(name = "amount_refunded_cents", nullable = false)
    private Integer amountRefundedCents;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BillingInvoiceStatus status;

    @Column(nullable = false, length = 50)
    private String reason;

    @Column(nullable = false)
    private Boolean commissionable;

    @Column(name = "commission_basis_cents", nullable = false)
    private Integer commissionBasisCents;

    @Column(name = "period_start")
    private OffsetDateTime periodStart;

    @Column(name = "period_end")
    private OffsetDateTime periodEnd;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @Column(name = "refund_status", nullable = false, length = 50)
    private String refundStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (amountPaidCents == null) amountPaidCents = 0;
        if (amountRefundedCents == null) amountRefundedCents = 0;
        if (currency == null || currency.isBlank()) currency = "usd";
        if (commissionable == null) commissionable = false;
        if (commissionBasisCents == null) commissionBasisCents = 0;
        if (refundStatus == null || refundStatus.isBlank()) refundStatus = "none";
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}