package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "referral_clicks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralClick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "affiliate_id", nullable = false)
    private Affiliate affiliate;

    @Column(name = "visitor_id", nullable = false, length = 255)
    private String visitorId;

    @Column(name = "ip_hash", length = 255)
    private String ipHash;

    @Column(name = "user_agent_hash", length = 255)
    private String userAgentHash;

    @Column(name = "landing_url", columnDefinition = "TEXT")
    private String landingUrl;

    @Column(name = "referrer_url", columnDefinition = "TEXT")
    private String referrerUrl;

    @Column(name = "clicked_at", nullable = false)
    private OffsetDateTime clickedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_user_id")
    private AppUser convertedUser;

    @PrePersist
    void onCreate() {
        if (clickedAt == null) {
            clickedAt = OffsetDateTime.now();
        }
    }
}