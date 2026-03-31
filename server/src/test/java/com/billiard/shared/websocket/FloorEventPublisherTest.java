package com.billiard.shared.websocket;

import static org.mockito.Mockito.verify;

import com.billiard.tables.TableStatus;
import com.billiard.tables.dto.BilliardTableResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class FloorEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private FloorEventPublisher floorEventPublisher;

    @BeforeEach
    void setUp() {
        floorEventPublisher = new FloorEventPublisher(messagingTemplate);
    }

    @Test
    void publishesCommittedFloorMessagesToTopic() {
        FloorMessage message = FloorMessage.tableStatusChanged(
                new BilliardTableResponse(
                        10L,
                        "Table 10",
                        5L,
                        "Pool",
                        TableStatus.IN_USE,
                        1,
                        2,
                        true,
                        Instant.parse("2026-03-28T02:00:00Z"),
                        Instant.parse("2026-03-28T02:05:00Z")
                ),
                Instant.parse("2026-03-28T02:05:00Z")
        );

        floorEventPublisher.publishAfterCommit(message);

        verify(messagingTemplate).convertAndSend(FloorEventPublisher.FLOOR_TOPIC, message);
    }
}
