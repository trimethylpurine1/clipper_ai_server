package com.clipperai.product.dto.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StartUpgradeResponse(
        String clientSecret,
        String publishableKey,

        UUID currentPlanId,
        String currentPlanName,

        UUID targetPlanId,
        String targetPlanName,

        Integer amountDueTodayCents,
        Integer nextRenewalAmountCents,
        String currency,

        OffsetDateTime currentPeriodEnd,

        String paymentReason
) {
}