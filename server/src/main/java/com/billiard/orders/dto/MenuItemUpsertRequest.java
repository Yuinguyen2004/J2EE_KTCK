package com.billiard.orders.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MenuItemUpsertRequest(
        @NotBlank
        @Size(max = 150)
        String name,
        @Size(max = 500)
        String description,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal price,
        @Size(max = 255)
        String imageUrl,
        Boolean active
) {
}
