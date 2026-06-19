package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "source_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "storage_bucket", length = 255)
    private String storageBucket;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "duration_seconds", precision = 12, scale = 3)
    private BigDecimal durationSeconds;

    private Integer width;

    private Integer height;

    @Column(name = "frame_rate", precision = 10, scale = 3)
    private BigDecimal frameRate;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (uploadedAt == null) uploadedAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "uploaded";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}