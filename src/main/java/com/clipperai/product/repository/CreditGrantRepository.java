package com.clipperai.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.CreditGrant;

public interface CreditGrantRepository extends JpaRepository<CreditGrant, UUID> {
    Optional<CreditGrant> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<CreditGrant> findByUserIdAndStatusOrderByCreatedAtAsc(String userId, String status);
}