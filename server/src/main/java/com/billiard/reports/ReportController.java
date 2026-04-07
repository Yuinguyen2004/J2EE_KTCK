package com.billiard.reports;

import com.billiard.reports.dto.RevenueReportResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * @desc Returns revenue totals bucketed by the requested calendar interval.
     * @param from start date inclusive
     * @param to end date inclusive
     * @param groupBy bucket size for the returned aggregation
     * @returns aggregated paid-invoice revenue for the requested range
     */
    @GetMapping("/revenue")
    public RevenueReportResponse revenue(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "DAY")
            RevenueGroupBy groupBy
    ) {
        return reportService.revenue(from, to, groupBy);
    }
}
