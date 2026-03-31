package com.billiard.reservations.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmReservationRequest(
        @NotNull
        Long tableId,
        @Size(max = 500)
        String notes
) {
}
