package com.billiard.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @desc Lightweight projection carrying one day's aggregated revenue from a
 * native query. The service normalizes paidDate into the appropriate
 * calendar bucket (WEEK/MONTH/YEAR) after retrieval.
 */
public record RevenueBucketRow(
        LocalDate paidDate,
        long invoiceCount,
        BigDecimal totalAmount
) {
}
