package com.billiard.orders.dto;

import com.billiard.billing.SessionStatus;
import com.billiard.orders.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long sessionId,
        SessionStatus sessionStatus,
        Long tableId,
        String tableName,
        Long customerId,
        String customerName,
        Long staffId,
        String staffName,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant orderedAt,
        String notes,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
