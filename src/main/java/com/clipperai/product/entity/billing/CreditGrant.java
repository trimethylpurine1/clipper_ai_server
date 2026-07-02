package com.clipperai.product.entity.billing;

import com.clipperai.product.entity.AppUser;
import com.clipperai.product.entity.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "credit_grants",
        indexes = {
                @Index(name = "idx_credit_grants_user_status", columnList = "user_id,status"),
                @Index(name = "idx_credit_grants_billing_invoice_id", columnList = "billing_invoice_id"),
                @Index(name = "idx_credit_grants_one_time_credit_product_id", columnList = "one_time_credit_product_id")
        }
)
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
    @JoinColumn(name = "billing_customer_id")
    private BillingCustomer billingCustomer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "one_time_credit_product_id")
    private OneTimeCreditProduct oneTimeCreditProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_invoice_id")
    private BillingInvoice billingInvoice;

    @Column(name = "stripe_payment_intent_id", unique = true, length = 255)
    private String stripePaymentIntentId;
  
    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 50)
    private CreditType creditType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CreditGrantStatus status;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (currency == null || currency.isBlank()) {
            currency = "usd";
        }

        if (status == null) {
            status = CreditGrantStatus.PENDING_PAYMENT;
        }

        if (reason == null || reason.isBlank()) {
            reason = "extra_video_credit_purchase";
        }

        if (remainingQuantity == null && quantity != null) {
            remainingQuantity = quantity;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}