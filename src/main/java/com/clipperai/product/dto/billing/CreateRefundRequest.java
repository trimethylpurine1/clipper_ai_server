package com.clipperai.product.dto.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRefundRequest(

        @Min(1)
        Integer amountCents,

        @NotBlank
        String reason

) {
}