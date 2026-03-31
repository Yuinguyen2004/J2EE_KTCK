package com.billiard.tables.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TableTypeUpsertRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @Size(max = 500)
        String description,
        Boolean active
) {
}
