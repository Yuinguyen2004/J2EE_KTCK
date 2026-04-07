package com.billiard.memberships.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MembershipTierUpsertRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotNull
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        BigDecimal discountPercent,
        @NotNull
        @DecimalMin(value = "0.0")
        BigDecimal minimumSpend,
        @Size(max = 500)
        String description,
        Boolean active
) {
}
