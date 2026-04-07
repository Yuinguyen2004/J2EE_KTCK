package com.billiard.tables.dto;

import com.billiard.tables.TableStatus;
import java.math.BigDecimal;

public record CustomerTableAvailabilityResponse(
        Long id,
        String name,
        Long tableTypeId,
        String tableTypeName,
        TableStatus status,
        BigDecimal pricePerHour
) {
}
