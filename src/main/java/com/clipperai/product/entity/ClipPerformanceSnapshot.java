package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clip_performance_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipPerformanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clip_post_id", nullable = false)
    private ClipPost clipPost;

    @Column(nullable = false)
    private Integer views;

    @Column(nullable = false)
    private Integer likes;

    @Column(nullable = false)
    private Integer comments;

    @Column(nullable = false)
    private Integer shares;

    @Column(nullable = false)
    private Integer saves;

    @Column(name = "engagement_rate", precision = 8, scale = 4)
    private BigDecimal engagementRate;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @PrePersist
    void onCreate() {
        if (views == null) {
            views = 0;
        }

        if (likes == null) {
            likes = 0;
        }

        if (comments == null) {
            comments = 0;
        }

        if (shares == null) {
            shares = 0;
        }

        if (saves == null) {
            saves = 0;
        }

        if (source == null) {
            source = "manual";
        }

        if (capturedAt == null) {
            capturedAt = OffsetDateTime.now();
        }
    }
}