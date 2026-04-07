package com.billiard.billing.dto;

import java.time.Instant;

public record SessionPauseResponse(
        Long id,
        Instant startedAt,
        Instant endedAt,
        String reason,
        Long durationSeconds
) {
}
