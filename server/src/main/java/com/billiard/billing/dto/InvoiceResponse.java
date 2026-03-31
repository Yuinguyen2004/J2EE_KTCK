package com.billiard.billing.dto;

import com.billiard.billing.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
        Long id,
        Long sessionId,
        Long tableId,
        String tableName,
        Long customerId,
        String customerName,
        Long issuedById,
        String issuedByName,
        InvoiceStatus status,
        BigDecimal tableAmount,
        BigDecimal orderAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        Instant issuedAt,
        Instant paidAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
