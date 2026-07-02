package com.clipperai.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clipperai.product.entity.billing.SubscriptionPlan;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {
    Optional<SubscriptionPlan> findByStripePriceId(String stripePriceId);
    Optional<SubscriptionPlan> findByName(String name);
    List<SubscriptionPlan> findByActiveTrue();
    
    List<SubscriptionPlan> findByActiveTrueOrderByMonthlyPriceCentsAsc();

    Optional<SubscriptionPlan> findByIdAndActiveTrue(UUID id);
}