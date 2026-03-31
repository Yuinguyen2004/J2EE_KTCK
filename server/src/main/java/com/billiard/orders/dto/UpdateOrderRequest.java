package com.billiard.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateOrderRequest(
        @NotEmpty
        List<@Valid CreateOrderItemRequest> items,
        @Size(max = 500)
        String notes
) {
}
