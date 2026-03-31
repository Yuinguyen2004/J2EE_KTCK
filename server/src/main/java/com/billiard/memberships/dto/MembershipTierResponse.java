package com.billiard.memberships.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MembershipTierResponse(
        Long id,
        String name,
        BigDecimal discountPercent,
        BigDecimal minimumSpend,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
