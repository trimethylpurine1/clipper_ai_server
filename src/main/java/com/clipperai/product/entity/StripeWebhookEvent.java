package com.clipperai.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "stripe_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "stripe_event_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stripe_event_id", nullable = false, unique = true, length = 255)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(name = "stripe_object_id", length = 255)
    private String stripeObjectId;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (status == null || status.isBlank()) status = "received";
        if (receivedAt == null) receivedAt = OffsetDateTime.now();
    }
}