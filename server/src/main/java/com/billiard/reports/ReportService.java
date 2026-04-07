package com.billiard.reports;

import com.billiard.billing.InvoiceRepository;
import com.billiard.reports.dto.RevenueBucketResponse;
import com.billiard.reports.dto.RevenueBucketRow;
import com.billiard.reports.dto.RevenueReportResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {

    private static final DateTimeFormatter DAY_LABEL_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern(
            "yyyy-MM",
            Locale.ROOT
    );

    private final InvoiceRepository invoiceRepository;

    public ReportService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * @desc Aggregates paid invoice totals into calendar buckets for the requested
     * inclusive date range.
     * @param from start date inclusive
     * @param to end date inclusive
     * @param groupBy calendar bucket size used to aggregate revenue
     * @returns zero-filled revenue buckets plus overall totals for the requested range
     * @throws ResponseStatusException when the date range is invalid
     */
    public RevenueReportResponse revenue(
            LocalDate from,
            LocalDate to,
            RevenueGroupBy groupBy
    ) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to are required");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "from must be on or before to"
            );
        }

        RevenueGroupBy resolvedGroupBy = groupBy == null ? RevenueGroupBy.DAY : groupBy;
        Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1L).atStartOfDay(ZoneOffset.UTC).toInstant();

        LinkedHashMap<LocalDate, BucketAccumulator> buckets = initializeBuckets(
                from,
                to,
                resolvedGroupBy
        );

        List<RevenueBucketRow> rows = invoiceRepository.aggregateDailyRevenue(
                fromInclusive,
                toExclusive
        );

        for (RevenueBucketRow row : rows) {
            LocalDate bucketStart = normalizeBucketStart(row.paidDate(), resolvedGroupBy);
            BucketAccumulator bucket = buckets.get(bucketStart);
            if (bucket != null) {
                bucket.add(row.invoiceCount(), row.totalAmount());
            }
        }

        List<RevenueBucketResponse> bucketResponses = buckets.entrySet().stream()
                .map(entry -> toBucketResponse(entry.getKey(), entry.getValue(), from, to, resolvedGroupBy))
                .toList();

        long invoiceCount = bucketResponses.stream()
                .mapToLong(RevenueBucketResponse::invoiceCount)
                .sum();
        BigDecimal totalAmount = bucketResponses.stream()
                .map(RevenueBucketResponse::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RevenueReportResponse(
                from,
                to,
                resolvedGroupBy,
                invoiceCount,
                totalAmount,
                bucketResponses
        );
    }

    private LinkedHashMap<LocalDate, BucketAccumulator> initializeBuckets(
            LocalDate from,
            LocalDate to,
            RevenueGroupBy groupBy
    ) {
        LinkedHashMap<LocalDate, BucketAccumulator> buckets = new LinkedHashMap<>();
        LocalDate cursor = normalizeBucketStart(from, groupBy);
        LocalDate lastBucket = normalizeBucketStart(to, groupBy);

        while (!cursor.isAfter(lastBucket)) {
            buckets.put(cursor, new BucketAccumulator());
            cursor = nextBucketStart(cursor, groupBy);
        }

        return buckets;
    }

    private RevenueBucketResponse toBucketResponse(
            LocalDate bucketStart,
            BucketAccumulator bucket,
            LocalDate from,
            LocalDate to,
            RevenueGroupBy groupBy
    ) {
        LocalDate effectiveStart = bucketStart.isBefore(from) ? from : bucketStart;
        LocalDate effectiveEnd = calendarBucketEnd(bucketStart, groupBy);
        if (effectiveEnd.isAfter(to)) {
            effectiveEnd = to;
        }

        return new RevenueBucketResponse(
                formatLabel(bucketStart, groupBy),
                effectiveStart,
                effectiveEnd,
                bucket.invoiceCount(),
                bucket.totalAmount()
        );
    }

    private LocalDate normalizeBucketStart(LocalDate date, RevenueGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private LocalDate nextBucketStart(LocalDate bucketStart, RevenueGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> bucketStart.plusDays(1L);
            case WEEK -> bucketStart.plusWeeks(1L);
            case MONTH -> bucketStart.plusMonths(1L);
            case YEAR -> bucketStart.plusYears(1L);
        };
    }

    private LocalDate calendarBucketEnd(LocalDate bucketStart, RevenueGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> bucketStart;
            case WEEK -> bucketStart.plusDays(6L);
            case MONTH -> bucketStart.with(TemporalAdjusters.lastDayOfMonth());
            case YEAR -> bucketStart.with(TemporalAdjusters.lastDayOfYear());
        };
    }

    private String formatLabel(LocalDate bucketStart, RevenueGroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> DAY_LABEL_FORMAT.format(bucketStart);
            case WEEK -> String.format(
                    Locale.ROOT,
                    "%d-W%02d",
                    bucketStart.get(IsoFields.WEEK_BASED_YEAR),
                    bucketStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            );
            case MONTH -> MONTH_LABEL_FORMAT.format(bucketStart);
            case YEAR -> Integer.toString(bucketStart.getYear());
        };
    }

    private static final class BucketAccumulator {

        private long invoiceCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private void add(long count, BigDecimal amount) {
            invoiceCount += count;
            totalAmount = totalAmount.add(amount == null ? BigDecimal.ZERO : amount);
        }

        private long invoiceCount() {
            return invoiceCount;
        }

        private BigDecimal totalAmount() {
            return totalAmount;
        }
    }
}
