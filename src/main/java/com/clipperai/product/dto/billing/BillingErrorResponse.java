package com.clipperai.product.dto.billing;

public record BillingErrorResponse(
        String code,
        String message
) {
}