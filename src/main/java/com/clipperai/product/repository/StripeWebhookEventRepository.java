package com.clipperai.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.StripeWebhookEvent;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEvent, UUID> {
    Optional<StripeWebhookEvent> findByStripeEventId(String stripeEventId);

    boolean existsByStripeEventIdAndStatus(String stripeEventId, String status);
}