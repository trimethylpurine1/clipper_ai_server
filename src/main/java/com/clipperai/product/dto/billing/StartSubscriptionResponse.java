package com.clipperai.product.dto.billing;

import java.util.UUID;

public record StartSubscriptionResponse(
        UUID localSubscriptionId,
        UUID planId,
        String stripeSubscriptionId,
        String clientSecret,
        String stripeStatus,
        String localStatus
) {
}