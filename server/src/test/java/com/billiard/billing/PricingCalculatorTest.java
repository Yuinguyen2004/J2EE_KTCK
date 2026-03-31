package com.billiard.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.billiard.billing.PricingCalculator.PauseWindow;
import com.billiard.billing.PricingCalculator.PricingRuleInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PricingCalculatorTest {

    private PricingCalculator pricingCalculator;
    private List<PricingRuleInput> defaultPricingRules;

    @BeforeEach
    void setUp() {
        pricingCalculator = new PricingCalculator();
        defaultPricingRules = List.of(
                new PricingRuleInput(15, new BigDecimal("2000.00")),
                new PricingRuleInput(15, new BigDecimal("1000.00"))
        );
    }

    @Test
    void calculatesSimpleSessionFromSpike() {
        var result = pricingCalculator.calculate(
                Instant.parse("2026-03-27T02:00:00Z"),
                Instant.parse("2026-03-27T02:45:00Z"),
                List.of(),
                defaultPricingRules,
                BigDecimal.ZERO
        );

        assertThat(result.totalSeconds()).isEqualTo(45L * 60L);
        assertThat(result.pausedSeconds()).isZero();
        assertThat(result.billableMinutes()).isEqualTo(45L);
        assertThat(result.grossAmount()).isEqualByComparingTo("60000.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.netTableAmount()).isEqualByComparingTo("60000.00");
    }

    @Test
    void calculatesMultiplePausesFromSpike() {
        var result = pricingCalculator.calculate(
                Instant.parse("2026-03-27T03:00:00Z"),
                Instant.parse("2026-03-27T05:00:00Z"),
                List.of(
                        new PauseWindow(
                                Instant.parse("2026-03-27T03:20:00Z"),
                                Instant.parse("2026-03-27T03:30:00Z")
                        ),
                        new PauseWindow(
                                Instant.parse("2026-03-27T04:00:00Z"),
                                Instant.parse("2026-03-27T04:15:00Z")
                        )
                ),
                defaultPricingRules,
                BigDecimal.ZERO
        );

        assertThat(result.pausedSeconds()).isEqualTo(25L * 60L);
        assertThat(result.billableMinutes()).isEqualTo(95L);
        assertThat(result.grossAmount()).isEqualByComparingTo("110000.00");
    }

    @Test
    void appliesMembershipDiscountFromSpike() {
        var result = pricingCalculator.calculate(
                Instant.parse("2026-03-27T07:00:00Z"),
                Instant.parse("2026-03-27T07:45:00Z"),
                List.of(),
                defaultPricingRules,
                new BigDecimal("10.00")
        );

        assertThat(result.grossAmount()).isEqualByComparingTo("60000.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("6000.00");
        assertThat(result.netTableAmount()).isEqualByComparingTo("54000.00");
    }

    @Test
    void roundsPartialBillableMinuteUpOnce() {
        var result = pricingCalculator.calculate(
                Instant.parse("2026-03-27T08:00:00Z"),
                Instant.parse("2026-03-27T08:01:01Z"),
                List.of(),
                defaultPricingRules,
                BigDecimal.ZERO
        );

        assertThat(result.billableSeconds()).isEqualTo(61L);
        assertThat(result.billableMinutes()).isEqualTo(2L);
        assertThat(result.grossAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void rejectsOverlappingPauses() {
        assertThatThrownBy(() -> pricingCalculator.calculate(
                Instant.parse("2026-03-27T09:00:00Z"),
                Instant.parse("2026-03-27T10:00:00Z"),
                List.of(
                        new PauseWindow(
                                Instant.parse("2026-03-27T09:10:00Z"),
                                Instant.parse("2026-03-27T09:20:00Z")
                        ),
                        new PauseWindow(
                                Instant.parse("2026-03-27T09:15:00Z"),
                                Instant.parse("2026-03-27T09:25:00Z")
                        )
                ),
                defaultPricingRules,
                BigDecimal.ZERO
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsNonFinalOpenEndedPricingRule() {
        assertThatThrownBy(() -> pricingCalculator.calculate(
                Instant.parse("2026-03-27T11:00:00Z"),
                Instant.parse("2026-03-27T12:00:00Z"),
                List.of(),
                List.of(
                        new PricingRuleInput(null, new BigDecimal("2000.00")),
                        new PricingRuleInput(15, new BigDecimal("1000.00"))
                ),
                BigDecimal.ZERO
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
