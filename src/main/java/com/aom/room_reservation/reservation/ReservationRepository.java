package com.aom.room_reservation.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /**
     * True if the room has an ACTIVE reservation overlapping [start, end).
     * Two half-open intervals overlap when each one starts before the other ends.
     */
    default boolean existsOverlapping(Long roomId, Instant start, Instant end) {
        return existsOverlapping(roomId, start, end, ReservationStatus.ACTIVE);
    }

    @Query("""
            select count(r) > 0 from Reservation r
            where r.room.id = :roomId
              and r.status = :status
              and r.startTime < :end
              and r.endTime > :start
            """)
    boolean existsOverlapping(@Param("roomId") Long roomId,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("status") ReservationStatus status);
}
