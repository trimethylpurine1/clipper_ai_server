package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transcript_segments",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"transcript_id", "segment_index"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transcript_id", nullable = false)
    private Transcript transcript;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex;

    @Column(name = "start_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal startSeconds;

    @Column(name = "end_seconds", nullable = false, precision = 12, scale = 3)
    private BigDecimal endSeconds;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}