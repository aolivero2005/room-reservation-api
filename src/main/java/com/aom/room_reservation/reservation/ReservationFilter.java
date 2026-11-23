package com.aom.room_reservation.reservation;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Optional search criteria for reservations; null fields are not applied.
 *
 * @param roomId only reservations of this room
 * @param date   only reservations that overlap this calendar day (in {@code zone})
 * @param zone   time zone used to interpret {@code date}; UTC when null
 * @param status only reservations in this status
 */
public record ReservationFilter(Long roomId, LocalDate date, ZoneId zone, ReservationStatus status) {

    public ReservationFilter {
        if (zone == null) {
            zone = ZoneOffset.UTC;
        }
    }
}
