package com.billiard.orders.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderItemRequest(
        @NotNull
        Long menuItemId,
        @NotNull
        @Min(1)
        @Max(9999)
        Integer quantity
) {
}
