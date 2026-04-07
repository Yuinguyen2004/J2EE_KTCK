package com.billiard.reports.dto;

import com.billiard.reports.RevenueGroupBy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueReportResponse(
        LocalDate from,
        LocalDate to,
        RevenueGroupBy groupBy,
        long invoiceCount,
        BigDecimal totalAmount,
        List<RevenueBucketResponse> buckets
) {
}
