package com.billiard.chat.dto;

import java.time.Instant;

public record ChatConversationSummaryResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerEmail,
        Instant lastMessageAt,
        String latestMessagePreview
) {
}
