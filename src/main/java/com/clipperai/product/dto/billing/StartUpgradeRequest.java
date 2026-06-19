package com.clipperai.product.dto.billing;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartUpgradeRequest(

        @NotNull
        UUID targetPlanId

) {
}