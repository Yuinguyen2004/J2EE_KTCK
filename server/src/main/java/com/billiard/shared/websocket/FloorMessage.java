package com.billiard.shared.websocket;

import com.billiard.billing.dto.TableSessionResponse;
import com.billiard.reservations.dto.ReservationResponse;
import com.billiard.tables.dto.BilliardTableResponse;
import java.time.Instant;

public record FloorMessage(
        FloorEventType type,
        BilliardTableResponse table,
        TableSessionResponse session,
        ReservationResponse reservation,
        Instant occurredAt
) {

    public static FloorMessage tableStatusChanged(
            BilliardTableResponse table,
            Instant occurredAt
    ) {
        return new FloorMessage(FloorEventType.TABLE_STATUS_CHANGED, table, null, null, occurredAt);
    }

    public static FloorMessage sessionChanged(
            TableSessionResponse session,
            Instant occurredAt
    ) {
        return new FloorMessage(FloorEventType.SESSION_CHANGED, null, session, null, occurredAt);
    }

    public static FloorMessage reservationChanged(
            ReservationResponse reservation,
            Instant occurredAt
    ) {
        return new FloorMessage(
                FloorEventType.RESERVATION_CHANGED,
                null,
                null,
                reservation,
                occurredAt
        );
    }
}
