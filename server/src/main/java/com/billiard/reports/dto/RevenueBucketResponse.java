package com.billiard.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueBucketResponse(
        String label,
        LocalDate bucketStart,
        LocalDate bucketEnd,
        long invoiceCount,
        BigDecimal totalAmount
) {
}
