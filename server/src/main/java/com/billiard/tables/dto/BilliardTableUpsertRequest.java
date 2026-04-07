package com.billiard.tables.dto;

import com.billiard.tables.TableStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BilliardTableUpsertRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotNull
        Long tableTypeId,
        @NotNull
        TableStatus status,
        @NotNull
        Integer floorPositionX,
        @NotNull
        Integer floorPositionY,
        Boolean active
) {
}
