package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "clip_candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_video_id", nullable = false)
    private SourceVideo sourceVideo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "start_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal startSeconds;

    @Column(name = "end_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal endSeconds;

    @Column(length = 255)
    private String title;

    @Column(name = "hook_text", columnDefinition = "TEXT")
    private String hookText;

    @Column(name = "suggested_caption", columnDefinition = "TEXT")
    private String suggestedCaption;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_metadata", columnDefinition = "jsonb")
    private Map<String, Object> aiMetadata;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (status == null) status = "suggested";
    }
}