package com.billiard.tables.dto;

import java.time.Instant;

public record TableTypeResponse(
        Long id,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
