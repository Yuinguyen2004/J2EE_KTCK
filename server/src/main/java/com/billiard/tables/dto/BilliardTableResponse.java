package com.billiard.tables.dto;

import com.billiard.tables.TableStatus;
import java.time.Instant;

public record BilliardTableResponse(
        Long id,
        String name,
        Long tableTypeId,
        String tableTypeName,
        TableStatus status,
        Integer floorPositionX,
        Integer floorPositionY,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
