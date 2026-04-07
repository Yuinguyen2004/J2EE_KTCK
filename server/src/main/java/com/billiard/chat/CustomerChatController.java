package com.billiard.chat;

import com.billiard.chat.dto.ChatConversationResponse;
import com.billiard.chat.dto.ChatMessageRequest;
import com.billiard.chat.dto.ChatMessageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/chat")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerChatController {

    private final ChatService chatService;

    public CustomerChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ChatConversationResponse getConversation(Authentication authentication) {
        return chatService.getCustomerConversation(authentication.getName());
    }

    @PostMapping("/messages")
    public ChatMessageResponse sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication
    ) {
        return chatService.sendCustomerMessage(authentication.getName(), request.content());
    }
}
