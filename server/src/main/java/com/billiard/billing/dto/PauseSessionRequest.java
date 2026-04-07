package com.billiard.billing.dto;

import jakarta.validation.constraints.Size;

public record PauseSessionRequest(@Size(max = 255) String reason) {
}
