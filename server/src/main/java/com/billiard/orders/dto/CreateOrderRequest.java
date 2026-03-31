package com.billiard.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull
        Long sessionId,
        @NotEmpty
        List<@Valid CreateOrderItemRequest> items,
        @Size(max = 500)
        String notes
) {
}
