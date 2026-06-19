package com.clipperai.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.BillingRefund;

public interface BillingRefundRepository extends JpaRepository<BillingRefund, UUID> {
    Optional<BillingRefund> findByStripeRefundId(String stripeRefundId);
}