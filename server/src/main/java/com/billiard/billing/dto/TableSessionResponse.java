package com.billiard.billing.dto;

import com.billiard.billing.SessionStatus;
import com.billiard.tables.TableStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TableSessionResponse(
        Long id,
        Long tableId,
        String tableName,
        TableStatus tableStatus,
        Long customerId,
        String customerName,
        Long staffId,
        String staffName,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Long elapsedSeconds,
        Long billableSeconds,
        Long totalPausedSeconds,
        BigDecimal totalAmount,
        String notes,
        List<SessionPauseResponse> pauses
) {
}
