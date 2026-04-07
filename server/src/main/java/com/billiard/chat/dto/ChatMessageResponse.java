package com.billiard.chat.dto;

import com.billiard.chat.ChatSenderRole;
import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        ChatSenderRole senderRole,
        String senderName,
        String content,
        Instant sentAt
) {
}
