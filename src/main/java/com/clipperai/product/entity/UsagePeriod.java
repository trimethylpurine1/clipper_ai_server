package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "usage_periods",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "period_start", "period_end"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsagePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private UserSubscription subscription;

    @Column(name = "period_start", nullable = false)
    private OffsetDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private OffsetDateTime periodEnd;

    @Column(name = "source_seconds_used", nullable = false)
    private Integer sourceSecondsUsed;

    @Column(name = "rendered_clips_used", nullable = false)
    private Integer renderedClipsUsed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (sourceSecondsUsed == null) {
            sourceSecondsUsed = 0;
        }

        if (renderedClipsUsed == null) {
            renderedClipsUsed = 0;
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}