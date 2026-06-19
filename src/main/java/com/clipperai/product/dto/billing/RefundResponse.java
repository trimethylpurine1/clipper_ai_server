package com.clipperai.product.dto.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID billingInvoiceId,

        String stripeRefundId,
        String stripeChargeId,
        String stripePaymentIntentId,

        Integer amountRefundedCents,
        String currency,

        String status,
        String reason,
        String refundType,

        OffsetDateTime createdAt,
        OffsetDateTime processedAt
) {
}