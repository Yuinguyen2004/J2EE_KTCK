package com.billiard.chat;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = {"conversation", "conversation.customer", "conversation.customer.user", "sender"})
    List<ChatMessage> findAllByConversation_IdOrderByCreatedAtAsc(Long conversationId);
}
