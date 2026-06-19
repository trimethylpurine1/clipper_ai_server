package com.clipperai.product.dto.billing;

import jakarta.validation.constraints.Min;

public record StartExtraCreditPurchaseRequest(

        @Min(1)
        Integer quantity

) {
}