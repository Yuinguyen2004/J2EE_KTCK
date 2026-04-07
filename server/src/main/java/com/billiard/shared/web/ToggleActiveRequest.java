package com.billiard.shared.web;

import jakarta.validation.constraints.NotNull;

public record ToggleActiveRequest(@NotNull Boolean active) {
}
