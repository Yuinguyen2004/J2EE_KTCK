package com.billiard.tables.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PricingRuleUpsertRequest(
        @NotNull
        Long tableTypeId,
        @Min(1)
        Integer blockMinutes,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal pricePerMinute,
        @NotNull
        Integer sortOrder,
        Boolean active
) {
}
