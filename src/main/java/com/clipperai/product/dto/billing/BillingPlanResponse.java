package com.clipperai.product.dto.billing;

import java.util.UUID;

public record BillingPlanResponse(
        UUID id,
        String name,
        String stripeProductId,
        String stripePriceId,
        Integer monthlyPriceCents,
        String currency,
        String billingInterval,
        Integer sourceMinutesPerMonth,
        Integer renderedClipsPerMonth,
        Integer maxVideoMinutes,
        Integer maxFileSizeMb,
        Boolean allow4k,
        Boolean active
) {
}