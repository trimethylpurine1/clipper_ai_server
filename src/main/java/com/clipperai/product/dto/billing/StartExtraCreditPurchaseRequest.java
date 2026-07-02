package com.clipperai.product.dto.billing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StartExtraCreditPurchaseRequest(

		@NotNull
        @Min(1)
		@Max(10)
        Integer quantity

) {
}