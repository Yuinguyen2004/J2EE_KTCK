package com.billiard.tables.dto;

import java.math.BigDecimal;

public record CustomerPricingPreviewResponse(
        Long tableTypeId,
        String tableTypeName,
        int durationMinutes,
        BigDecimal membershipDiscountPercent,
        String membershipTierName,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal estimatedTotal
) {
}
