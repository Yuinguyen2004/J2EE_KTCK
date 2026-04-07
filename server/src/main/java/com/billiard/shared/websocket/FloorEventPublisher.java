package com.billiard.shared.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FloorEventPublisher {

    public static final String FLOOR_TOPIC = "/topic/floor";

    private final SimpMessagingTemplate messagingTemplate;

    public FloorEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(FloorMessage message) {
        messagingTemplate.convertAndSend(FLOOR_TOPIC, message);
    }
}
