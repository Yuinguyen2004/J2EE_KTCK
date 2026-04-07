package com.billiard.reservations.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateReservationRequest(
        @NotNull
        Long tableId,
        @NotNull
        Long customerId,
        @NotNull
        @Future
        Instant reservedFrom,
        @NotNull
        @FutureOrPresent
        Instant reservedTo,
        @Min(1)
        Integer partySize,
        @Size(max = 500)
        String notes
) {
}
