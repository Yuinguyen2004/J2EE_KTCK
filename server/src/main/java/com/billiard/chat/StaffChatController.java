package com.billiard.chat;

import com.billiard.chat.dto.ChatConversationResponse;
import com.billiard.chat.dto.ChatConversationSummaryResponse;
import com.billiard.chat.dto.ChatMessageRequest;
import com.billiard.chat.dto.ChatMessageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/staff/chat")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class StaffChatController {

    private final ChatService chatService;

    public StaffChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public List<ChatConversationSummaryResponse> listConversations() {
        return chatService.listConversations();
    }

    @GetMapping("/conversations/{conversationId}")
    public ChatConversationResponse getConversation(@PathVariable Long conversationId) {
        return chatService.getConversation(conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ChatMessageResponse sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication
    ) {
        return chatService.sendStaffMessage(
                conversationId,
                request.content(),
                authentication.getName()
        );
    }
}
