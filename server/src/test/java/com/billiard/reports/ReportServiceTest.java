package com.billiard.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.billiard.billing.InvoiceRepository;
import com.billiard.reports.dto.RevenueBucketRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(invoiceRepository);
    }

    @Test
    void revenueGroupsPaidInvoicesByDayAndFillsEmptyDays() {
        when(invoiceRepository.aggregateDailyRevenue(any(), any())).thenReturn(List.of(
                new RevenueBucketRow(LocalDate.parse("2026-03-27"), 1, new BigDecimal("100.00")),
                new RevenueBucketRow(LocalDate.parse("2026-03-29"), 1, new BigDecimal("40.00"))
        ));

        var report = reportService.revenue(
                LocalDate.parse("2026-03-27"),
                LocalDate.parse("2026-03-29"),
                RevenueGroupBy.DAY
        );

        assertThat(report.groupBy()).isEqualTo(RevenueGroupBy.DAY);
        assertThat(report.invoiceCount()).isEqualTo(2);
        assertThat(report.totalAmount()).isEqualByComparingTo("140.00");
        assertThat(report.buckets()).hasSize(3);

        assertThat(report.buckets().get(0).label()).isEqualTo("2026-03-27");
        assertThat(report.buckets().get(0).bucketStart()).isEqualTo(LocalDate.parse("2026-03-27"));
        assertThat(report.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.parse("2026-03-27"));
        assertThat(report.buckets().get(0).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(0).totalAmount()).isEqualByComparingTo("100.00");

        assertThat(report.buckets().get(1).label()).isEqualTo("2026-03-28");
        assertThat(report.buckets().get(1).invoiceCount()).isZero();
        assertThat(report.buckets().get(1).totalAmount()).isEqualByComparingTo("0.00");

        assertThat(report.buckets().get(2).label()).isEqualTo("2026-03-29");
        assertThat(report.buckets().get(2).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(2).totalAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void revenueGroupsInvoicesByIsoWeekAndTrimsPartialRangeBuckets() {
        when(invoiceRepository.aggregateDailyRevenue(any(), any())).thenReturn(List.of(
                new RevenueBucketRow(LocalDate.parse("2026-04-01"), 1, new BigDecimal("70.00")),
                new RevenueBucketRow(LocalDate.parse("2026-04-03"), 1, new BigDecimal("30.00")),
                new RevenueBucketRow(LocalDate.parse("2026-04-07"), 1, new BigDecimal("50.00"))
        ));

        var report = reportService.revenue(
                LocalDate.parse("2026-03-31"),
                LocalDate.parse("2026-04-08"),
                RevenueGroupBy.WEEK
        );

        assertThat(report.groupBy()).isEqualTo(RevenueGroupBy.WEEK);
        assertThat(report.invoiceCount()).isEqualTo(3);
        assertThat(report.totalAmount()).isEqualByComparingTo("150.00");
        assertThat(report.buckets()).hasSize(2);

        assertThat(report.buckets().get(0).label()).isEqualTo(weekLabel(LocalDate.parse("2026-03-30")));
        assertThat(report.buckets().get(0).bucketStart()).isEqualTo(LocalDate.parse("2026-03-31"));
        assertThat(report.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.parse("2026-04-05"));
        assertThat(report.buckets().get(0).invoiceCount()).isEqualTo(2);
        assertThat(report.buckets().get(0).totalAmount()).isEqualByComparingTo("100.00");

        assertThat(report.buckets().get(1).label()).isEqualTo(weekLabel(LocalDate.parse("2026-04-06")));
        assertThat(report.buckets().get(1).bucketStart()).isEqualTo(LocalDate.parse("2026-04-06"));
        assertThat(report.buckets().get(1).bucketEnd()).isEqualTo(LocalDate.parse("2026-04-08"));
        assertThat(report.buckets().get(1).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(1).totalAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void revenueGroupsInvoicesByMonthAndTrimsPartialRangeBuckets() {
        when(invoiceRepository.aggregateDailyRevenue(any(), any())).thenReturn(List.of(
                new RevenueBucketRow(LocalDate.parse("2026-03-05"), 1, new BigDecimal("120.00")),
                new RevenueBucketRow(LocalDate.parse("2026-04-09"), 1, new BigDecimal("60.00"))
        ));

        var report = reportService.revenue(
                LocalDate.parse("2026-03-03"),
                LocalDate.parse("2026-04-10"),
                RevenueGroupBy.MONTH
        );

        assertThat(report.groupBy()).isEqualTo(RevenueGroupBy.MONTH);
        assertThat(report.invoiceCount()).isEqualTo(2);
        assertThat(report.totalAmount()).isEqualByComparingTo("180.00");
        assertThat(report.buckets()).hasSize(2);

        assertThat(report.buckets().get(0).label()).isEqualTo("2026-03");
        assertThat(report.buckets().get(0).bucketStart()).isEqualTo(LocalDate.parse("2026-03-03"));
        assertThat(report.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.parse("2026-03-31"));
        assertThat(report.buckets().get(0).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(0).totalAmount()).isEqualByComparingTo("120.00");

        assertThat(report.buckets().get(1).label()).isEqualTo("2026-04");
        assertThat(report.buckets().get(1).bucketStart()).isEqualTo(LocalDate.parse("2026-04-01"));
        assertThat(report.buckets().get(1).bucketEnd()).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(report.buckets().get(1).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(1).totalAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void revenueGroupsInvoicesByYearAndTrimsPartialRangeBuckets() {
        when(invoiceRepository.aggregateDailyRevenue(any(), any())).thenReturn(List.of(
                new RevenueBucketRow(LocalDate.parse("2025-12-31"), 1, new BigDecimal("25.00")),
                new RevenueBucketRow(LocalDate.parse("2026-01-01"), 1, new BigDecimal("90.00"))
        ));

        var report = reportService.revenue(
                LocalDate.parse("2025-12-31"),
                LocalDate.parse("2026-01-02"),
                RevenueGroupBy.YEAR
        );

        assertThat(report.groupBy()).isEqualTo(RevenueGroupBy.YEAR);
        assertThat(report.invoiceCount()).isEqualTo(2);
        assertThat(report.totalAmount()).isEqualByComparingTo("115.00");
        assertThat(report.buckets()).hasSize(2);

        assertThat(report.buckets().get(0).label()).isEqualTo("2025");
        assertThat(report.buckets().get(0).bucketStart()).isEqualTo(LocalDate.parse("2025-12-31"));
        assertThat(report.buckets().get(0).bucketEnd()).isEqualTo(LocalDate.parse("2025-12-31"));
        assertThat(report.buckets().get(0).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(0).totalAmount()).isEqualByComparingTo("25.00");

        assertThat(report.buckets().get(1).label()).isEqualTo("2026");
        assertThat(report.buckets().get(1).bucketStart()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(report.buckets().get(1).bucketEnd()).isEqualTo(LocalDate.parse("2026-01-02"));
        assertThat(report.buckets().get(1).invoiceCount()).isEqualTo(1);
        assertThat(report.buckets().get(1).totalAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void revenueRejectsInvalidDateRange() {
        assertThatThrownBy(() -> reportService.revenue(
                LocalDate.parse("2026-04-09"),
                LocalDate.parse("2026-04-08"),
                RevenueGroupBy.MONTH
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static String weekLabel(LocalDate bucketStart) {
        return "%d-W%02d".formatted(
                bucketStart.get(IsoFields.WEEK_BASED_YEAR),
                bucketStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        );
    }
}
