package com.billiard.chat.dto;

import java.time.Instant;
import java.util.List;

public record ChatConversationResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerEmail,
        Instant lastMessageAt,
        List<ChatMessageResponse> messages
) {
}
