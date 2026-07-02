package com.clipperai.product.dto.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CancelSubscriptionResponse(
		
        UUID localSubscriptionId,
        String stripeSubscriptionId,
        String stripeStatus,
        String localStatus,
        Boolean cancelAtPeriodEnd,
        OffsetDateTime currentPeriodEnd,
        String message
        ) {

}
