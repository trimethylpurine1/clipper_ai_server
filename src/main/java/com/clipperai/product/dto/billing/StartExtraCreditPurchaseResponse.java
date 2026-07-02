package com.clipperai.product.dto.billing;

import java.util.UUID;

public record StartExtraCreditPurchaseResponse(
        String clientSecret,
        String publishableKey,

        UUID creditGrantId,
        UUID billingInvoiceId,

        Integer quantity,
        Integer creditsGranted,
        Integer amountDueCents,
        String currency,

        String localStatus
) {
}