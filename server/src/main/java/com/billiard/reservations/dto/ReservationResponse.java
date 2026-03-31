package com.billiard.reservations.dto;

import com.billiard.reservations.ReservationStatus;
import com.billiard.tables.TableStatus;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long tableId,
        String tableName,
        TableStatus tableStatus,
        Long customerId,
        String customerName,
        Long staffId,
        String staffName,
        ReservationStatus status,
        Instant reservedFrom,
        Instant reservedTo,
        Integer partySize,
        Instant checkedInAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
