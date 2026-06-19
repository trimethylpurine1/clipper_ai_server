package com.clipperai.product.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "clip_compliance_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClipComplianceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rendered_clip_id", nullable = false)
    private RenderedClip renderedClip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "overall_status", nullable = false, length = 50)
    private String overallStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist_json", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> checklistJson;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}