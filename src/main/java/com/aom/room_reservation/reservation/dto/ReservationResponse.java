package com.aom.room_reservation.reservation.dto;

import com.aom.room_reservation.reservation.Reservation;
import com.aom.room_reservation.reservation.ReservationStatus;

import java.time.Instant;

/** Times are returned in UTC (ISO-8601, e.g. 2030-01-15T13:00:00Z). */
public record ReservationResponse(
        Long id,
        Long roomId,
        String roomName,
        String responsible,
        Instant start,
        Instant end,
        ReservationStatus status,
        Instant createdAt
) {

    /** Must be called inside a transaction: it reads the (lazy) room name. */
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getRoom().getName(),
                reservation.getResponsible(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
