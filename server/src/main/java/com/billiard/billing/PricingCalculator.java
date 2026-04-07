package com.billiard.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * @desc Pure billing arithmetic that converts completed session timing, pauses,
 * and sequential pricing rules into an auditable table-charge snapshot.
 * The final configured rule acts as the fallback rate for any remaining
 * billable minutes beyond the explicitly sized blocks.
 */
@Service
public class PricingCalculator {

    /**
     * @desc Calculates the table-time charge for a completed session using
     * validated pause windows and ordered pricing rules.
     */
    public PricingCalculationResult calculate(
            Instant startedAt,
            Instant endedAt,
            List<PauseWindow> pauses,
            List<PricingRuleInput> pricingRules,
            BigDecimal membershipDiscountPercent
    ) {
        if (startedAt == null || endedAt == null || !endedAt.isAfter(startedAt)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Completed session timestamps are required for billing"
            );
        }
        if (pricingRules == null || pricingRules.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "At least one active pricing rule is required"
            );
        }

        long totalSeconds = Duration.between(startedAt, endedAt).getSeconds();
        long pausedSeconds = calculatePausedSeconds(startedAt, endedAt, pauses);
        long billableSeconds = Math.max(totalSeconds - pausedSeconds, 0L);
        long billableMinutes = billableSeconds == 0L ? 0L : (billableSeconds + 59L) / 60L;

        BigDecimal grossAmount = calculateGrossAmount(billableMinutes, pricingRules);
        BigDecimal discountPercent = membershipDiscountPercent == null
                ? BigDecimal.ZERO
                : membershipDiscountPercent;
        BigDecimal discountAmount = grossAmount
                .multiply(discountPercent)
                .divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        BigDecimal netTableAmount = grossAmount.subtract(discountAmount);

        return new PricingCalculationResult(
                totalSeconds,
                pausedSeconds,
                billableSeconds,
                billableMinutes,
                grossAmount,
                discountAmount,
                netTableAmount
        );
    }

    private long calculatePausedSeconds(
            Instant sessionStart,
            Instant sessionEnd,
            List<PauseWindow> pauses
    ) {
        List<PauseWindow> orderedPauses = pauses == null
                ? List.of()
                : pauses.stream()
                        .sorted(Comparator.comparing(PauseWindow::startedAt))
                        .toList();

        Instant previousEnd = sessionStart;
        long totalPausedSeconds = 0L;
        for (PauseWindow pause : orderedPauses) {
            Instant pauseStart = pause.startedAt();
            Instant pauseEnd = pause.endedAt();
            if (pauseStart == null || pauseEnd == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Billing pauses must have both start and end timestamps"
                );
            }
            if (!pauseEnd.isAfter(pauseStart)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Pause end must be after pause start"
                );
            }
            if (pauseStart.isBefore(sessionStart) || pauseEnd.isAfter(sessionEnd)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Pauses must be fully contained inside the session"
                );
            }
            if (pauseStart.isBefore(previousEnd)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Pauses must not overlap"
                );
            }

            previousEnd = pauseEnd;
            totalPausedSeconds += Duration.between(pauseStart, pauseEnd).getSeconds();
        }
        return totalPausedSeconds;
    }

    private BigDecimal calculateGrossAmount(
            long billableMinutes,
            List<PricingRuleInput> pricingRules
    ) {
        long remainingMinutes = billableMinutes;
        BigDecimal grossAmount = BigDecimal.ZERO;

        for (int index = 0; index < pricingRules.size(); index++) {
            if (remainingMinutes == 0L) {
                break;
            }

            PricingRuleInput pricingRule = pricingRules.get(index);
            Integer blockMinutes = pricingRule.blockMinutes();
            if (blockMinutes != null && blockMinutes <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Pricing rule blockMinutes must be greater than zero"
                );
            }
            if (pricingRule.pricePerMinute() == null
                    || pricingRule.pricePerMinute().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Pricing rule pricePerMinute must be non-negative"
                );
            }
            if (blockMinutes == null && index != pricingRules.size() - 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only the final pricing rule may be open-ended"
                );
            }

            long coveredMinutes = index == pricingRules.size() - 1
                    ? remainingMinutes
                    : Math.min(remainingMinutes, blockMinutes.longValue());
            grossAmount = grossAmount.add(
                    pricingRule.pricePerMinute().multiply(BigDecimal.valueOf(coveredMinutes))
            );
            remainingMinutes -= coveredMinutes;
        }

        return grossAmount;
    }

    public record PauseWindow(
            Instant startedAt,
            Instant endedAt
    ) {
    }

    public record PricingRuleInput(
            Integer blockMinutes,
            BigDecimal pricePerMinute
    ) {
    }

    public record PricingCalculationResult(
            long totalSeconds,
            long pausedSeconds,
            long billableSeconds,
            long billableMinutes,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal netTableAmount
    ) {
    }
}
