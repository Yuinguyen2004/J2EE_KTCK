package com.billiard.customers.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        Long userId,
        String email,
        String fullName,
        String phone,
        boolean active,
        Long membershipTierId,
        String membershipTierName,
        String notes,
        Instant memberSince,
        Instant createdAt,
        Instant updatedAt
) {
}
