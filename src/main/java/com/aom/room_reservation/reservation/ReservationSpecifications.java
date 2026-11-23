package com.aom.room_reservation.reservation;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Builds the dynamic WHERE clause for the reservation listing from a {@link ReservationFilter}. */
final class ReservationSpecifications {

    private ReservationSpecifications() {
    }

    static Specification<Reservation> from(ReservationFilter filter) {
        List<Specification<Reservation>> specs = new ArrayList<>();
        if (filter.roomId() != null) {
            specs.add(hasRoom(filter.roomId()));
        }
        if (filter.date() != null) {
            specs.add(overlapsDay(filter.date(), filter.zone()));
        }
        if (filter.status() != null) {
            specs.add(hasStatus(filter.status()));
        }
        return Specification.allOf(specs);
    }

    static Specification<Reservation> hasRoom(Long roomId) {
        return (root, query, cb) -> cb.equal(root.get("room").get("id"), roomId);
    }

    /**
     * Reservations that overlap the day [00:00, next day 00:00) in the given zone, not only the ones
     * starting that day: a booking from 23:00 to 01:00 shows up on both days.
     */
    static Specification<Reservation> overlapsDay(LocalDate date, ZoneId zone) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("startTime"), dayEnd),
                cb.greaterThan(root.get("endTime"), dayStart));
    }

    static Specification<Reservation> hasStatus(ReservationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
