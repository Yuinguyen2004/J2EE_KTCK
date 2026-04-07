package com.billiard.chat;

import com.billiard.chat.dto.ChatMessageResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatEvents {

    public static final String STAFF_TOPIC = "/topic/chat/staff";
    private final SimpMessagingTemplate messagingTemplate;

    public ChatEvents(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void messageSent(Long customerId, ChatMessageResponse message) {
        messagingTemplate.convertAndSend(STAFF_TOPIC, message);
        messagingTemplate.convertAndSend(customerTopic(customerId), message);
    }

    public static String customerTopic(Long customerId) {
        return "/topic/chat/customer/" + customerId;
    }
}
