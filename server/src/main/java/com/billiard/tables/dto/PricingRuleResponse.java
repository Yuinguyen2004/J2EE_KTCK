package com.billiard.tables.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingRuleResponse(
        Long id,
        Long tableTypeId,
        String tableTypeName,
        Integer blockMinutes,
        BigDecimal pricePerMinute,
        Integer sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
