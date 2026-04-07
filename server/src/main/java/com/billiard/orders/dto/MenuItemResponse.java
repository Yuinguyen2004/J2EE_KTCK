package com.billiard.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MenuItemResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
