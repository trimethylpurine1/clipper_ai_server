package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "affiliates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Affiliate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_duration_months", nullable = false)
    private Integer commissionDurationMonths;

    @Column(name = "cookie_duration_days", nullable = false)
    private Integer cookieDurationDays;

    @Column(name = "payout_delay_days", nullable = false)
    private Integer payoutDelayDays;

    @Column(name = "minimum_payout_cents", nullable = false)
    private Integer minimumPayoutCents;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();

        if (status == null) {
            status = "pending";
        }

        if (commissionRate == null) {
            commissionRate = new BigDecimal("0.3000");
        }

        if (commissionDurationMonths == null) {
            commissionDurationMonths = 12;
        }

        if (cookieDurationDays == null) {
            cookieDurationDays = 90;
        }

        if (payoutDelayDays == null) {
            payoutDelayDays = 45;
        }

        if (minimumPayoutCents == null) {
            minimumPayoutCents = 5000;
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