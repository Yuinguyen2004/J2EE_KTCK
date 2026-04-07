package com.billiard.shared.websocket;

import com.billiard.billing.dto.TableSessionResponse;
import com.billiard.reservations.dto.ReservationResponse;
import com.billiard.tables.dto.BilliardTableResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class FloorEvents {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public FloorEvents(ApplicationEventPublisher applicationEventPublisher, Clock clock) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    public void tableStatusChanged(BilliardTableResponse table) {
        publish(FloorMessage.tableStatusChanged(table, Instant.now(clock)));
    }

    public void sessionChanged(TableSessionResponse session) {
        publish(FloorMessage.sessionChanged(session, Instant.now(clock)));
    }

    public void reservationChanged(ReservationResponse reservation) {
        publish(FloorMessage.reservationChanged(reservation, Instant.now(clock)));
    }

    private void publish(FloorMessage message) {
        applicationEventPublisher.publishEvent(message);
    }
}
