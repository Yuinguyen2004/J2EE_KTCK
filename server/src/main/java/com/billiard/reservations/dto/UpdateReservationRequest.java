package com.billiard.reservations.dto;

import com.billiard.reservations.ReservationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateReservationRequest(
        @NotNull
        Long tableId,
        @NotNull
        Long customerId,
        @NotNull
        ReservationStatus status,
        @NotNull
        Instant reservedFrom,
        @NotNull
        Instant reservedTo,
        @Min(1)
        Integer partySize,
        @Size(max = 500)
        String notes
) {
}
