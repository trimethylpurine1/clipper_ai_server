package com.clipperai.product.dto.billing;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.clipperai.product.entity.billing.BillingCustomer;

public record BillingCustomerResponse(
        UUID id,
        String stripeCustomerId,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BillingCustomerResponse from(BillingCustomer billingCustomer) {
        return new BillingCustomerResponse(
                billingCustomer.getId(),
                billingCustomer.getStripeCustomerId(),
                billingCustomer.getEmail(),
                billingCustomer.getCreatedAt(),
                billingCustomer.getUpdatedAt()
        );
    }
}