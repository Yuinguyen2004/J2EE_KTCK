package com.billiard.customers.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CustomerUpsertRequest(
        @NotNull
        Long userId,
        Long membershipTierId,
        @Size(max = 500)
        String notes,
        Instant memberSince
) {
}
