package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "affiliate_attributions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffiliateAttribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private Affiliate affiliate;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_click_id")
    private ReferralClick referralClick;

    @Column(name = "attributed_at", nullable = false)
    private OffsetDateTime attributedAt;

    @Column(name = "commission_ends_at", nullable = false)
    private OffsetDateTime commissionEndsAt;

    @PrePersist
    void onCreate() {
        if (attributedAt == null) {
            attributedAt = OffsetDateTime.now();
        }
    }
}