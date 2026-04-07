package com.billiard.billing.dto;

import jakarta.validation.constraints.Size;

public record StartSessionRequest(
        Long customerId,
        Boolean overrideReserved,
        @Size(max = 500)
        String notes
) {
}
