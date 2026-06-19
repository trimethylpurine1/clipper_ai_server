package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;
    
    @Column(name = "stripe_product_id", length = 255)
    private String stripeProductId;

    @Column(name = "stripe_price_id", unique = true, length = 255)
    private String stripePriceId;

    @Column(name = "monthly_price_cents", nullable = false)
    private Integer monthlyPriceCents;
    
    @Column(nullable = false, length = 10)
    private String currency;
    
    @Column(name = "billing_interval", nullable = false, length = 50)
    private String billingInterval;

    @Column(name = "source_minutes_per_month", nullable = false)
    private Integer sourceMinutesPerMonth;

    @Column(name = "rendered_clips_per_month", nullable = false)
    private Integer renderedClipsPerMonth;

    @Column(name = "max_video_minutes")
    private Integer maxVideoMinutes;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;

    @Column(name = "allow_4k", nullable = false)
    private Boolean allow4k;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (allow4k == null) allow4k = false;
        if (active == null) active = true;
    }
}