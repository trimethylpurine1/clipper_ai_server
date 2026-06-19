package com.clipperai.product.dto.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpgradePreviewResponse(
        UUID currentPlanId,
        String currentPlanName,
        Integer currentPlanMonthlyPriceCents,

        UUID targetPlanId,
        String targetPlanName,
        Integer targetPlanMonthlyPriceCents,

        Integer amountDueTodayCents,
        Integer nextRenewalAmountCents,
        String currency,

        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,

        CurrentUsage currentUsage,
        PlanLimits currentLimits,
        PlanLimits newLimits,
        RemainingAfterUpgrade remainingAfterUpgrade,

        String explanation
) {

    public record CurrentUsage(
            Integer sourceSecondsUsed,
            Integer renderedClipsUsed
    ) {
    }

    public record PlanLimits(
            Integer sourceSecondsLimit,
            Integer renderedClipsLimit,
            Integer maxVideoMinutes,
            Integer maxFileSizeMb,
            Boolean allow4k
    ) {
    }

    public record RemainingAfterUpgrade(
            Integer sourceSecondsRemaining,
            Integer renderedClipsRemaining
    ) {
    }
}