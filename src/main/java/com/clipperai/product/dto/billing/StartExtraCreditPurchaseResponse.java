package com.clipperai.product.dto.billing;

import java.util.UUID;

public record StartExtraCreditPurchaseResponse(
        String clientSecret,
        String publishableKey,

        UUID creditGrantId,

        Integer quantity,
        Integer amountDueCents,
        String currency
) {
}