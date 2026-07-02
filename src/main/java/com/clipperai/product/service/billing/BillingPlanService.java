package com.clipperai.product.service.billing;

import com.clipperai.product.dto.billing.BillingPlanResponse;
import com.clipperai.product.entity.billing.SubscriptionPlan;
import com.clipperai.product.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public List<BillingPlanResponse> getActivePlans() {
        return subscriptionPlanRepository.findByActiveTrueOrderByMonthlyPriceCentsAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SubscriptionPlan getActivePlanOrThrow(UUID planId) {
        return subscriptionPlanRepository.findByIdAndActiveTrue(planId)
                .orElseThrow(() -> new IllegalArgumentException("Active billing plan not found."));
    }

    public SubscriptionPlan getPlanByStripePriceIdOrThrow(String stripePriceId) {
        return subscriptionPlanRepository.findByStripePriceId(stripePriceId)
                .orElseThrow(() -> new IllegalArgumentException("Billing plan not found for Stripe price ID."));
    }

    private BillingPlanResponse toResponse(SubscriptionPlan plan) {
        return new BillingPlanResponse(
        		
                plan.getId(),
                plan.getName(),
                plan.getStripeProductId(),
                plan.getStripePriceId(),
                plan.getMonthlyPriceCents(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                plan.getSourceMinutesPerMonth(),
                plan.getRenderedClipsPerMonth(),
                plan.getMaxVideoMinutes(),
                plan.getMaxFileSizeMb(),
                plan.getAllow4k(),
                plan.getActive()
        );
    }
}