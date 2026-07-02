package com.clipperai.product.entity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.clipperai.product.entity.billing.BillingInvoice;

@Entity
@Table(name = "commissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private Affiliate affiliate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribution_id")
    private AffiliateAttribution attribution;

    @Column(name = "stripe_invoice_id", unique = true, length = 255)
    private String stripeInvoiceId;

    @Column(name = "stripe_charge_id", length = 255)
    private String stripeChargeId;

    @Column(name = "amount_paid_cents", nullable = false)
    private Integer amountPaidCents;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount_cents", nullable = false)
    private Integer commissionAmountCents;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "available_after", nullable = false)
    private OffsetDateTime availableAfter;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_invoice_id")
    private BillingInvoice billingInvoice;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = "pending";
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}