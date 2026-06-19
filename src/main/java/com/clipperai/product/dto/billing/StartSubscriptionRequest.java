package com.clipperai.product.dto.billing;


import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartSubscriptionRequest(

        @NotNull
        UUID planId

) {
}