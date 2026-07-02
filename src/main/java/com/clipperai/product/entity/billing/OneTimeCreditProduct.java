package com.clipperai.product.entity.billing;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.clipperai.product.entity.*;

@Entity
@Table(
        name = "one_time_credit_products",
        indexes = {
                @Index(name = "idx_one_time_credit_products_code_active", columnList = "code,is_active"),
                @Index(name = "idx_one_time_credit_products_stripe_price_id", columnList = "stripe_price_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OneTimeCreditProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "stripe_product_id", length = 255)
    private String stripeProductId;

    @Column(name = "stripe_price_id", unique = true, length = 255)
    private String stripePriceId;

    @Column(name = "unit_amount_cents", nullable = false)
    private Integer unitAmountCents;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 50)
    private CreditType creditType;

    @Column(name = "credit_quantity", nullable = false)
    private Integer creditQuantity;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (currency == null || currency.isBlank()) {
            currency = "usd";
        }

        if (active == null) {
            active = true;
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
