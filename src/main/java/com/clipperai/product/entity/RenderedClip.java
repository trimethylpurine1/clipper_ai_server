package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rendered_clips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RenderedClip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_video_id", nullable = false)
    private SourceVideo sourceVideo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clip_candidate_id")
    private ClipCandidate clipCandidate;

    @Column(length = 255)
    private String title;

    @Column(name = "start_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal startSeconds;

    @Column(name = "end_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal endSeconds;

    @Column(name = "duration_seconds", precision = 12, scale = 3, insertable = false, updatable = false)
    private BigDecimal durationSeconds;

    @Column(name = "storage_bucket", length = 255)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "thumbnail_storage_key", columnDefinition = "TEXT")
    private String thumbnailStorageKey;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(name = "has_burned_captions", nullable = false)
    private Boolean hasBurnedCaptions;

    @Column(name = "caption_text", columnDefinition = "TEXT")
    private String captionText;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (hasBurnedCaptions == null) hasBurnedCaptions = false;
        if (status == null) status = "ready";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}