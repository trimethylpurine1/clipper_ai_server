package com.clipperai.product.dto.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingMeResponse(
        boolean hasBillingCustomer,
        String stripeCustomerId,

        UUID subscriptionId,
        String subscriptionStatus,
        String stripeSubscriptionStatus,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,

        UUID planId,
        String planName,
        Integer monthlyPriceCents,
        String currency,
        String billingInterval,

        Integer sourceMinutesPerMonth,
        Integer renderedClipsPerMonth,
        Integer maxVideoMinutes,
        Integer maxFileSizeMb,
        Boolean allow4k,

        Integer sourceSecondsUsed,
        Integer sourceSecondsLimit,
        Integer sourceSecondsRemaining,

        Integer renderedClipsUsed,
        Integer renderedClipsLimit,
        Integer renderedClipsRemaining,

        Integer activeExtraVideoCredits,

        boolean canUploadSourceVideo,
        boolean requiresEmailVerification,
        boolean requiresSubscription,
        boolean usageLimitReached
) {
}