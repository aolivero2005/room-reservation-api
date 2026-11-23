package com.aom.room_reservation.reservation;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.common.ConstraintViolations;
import com.aom.room_reservation.common.ResourceNotFoundException;
import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import com.aom.room_reservation.room.Room;
import com.aom.room_reservation.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReservationService {

    /** PostgreSQL-only exclusion constraint against overlapping ACTIVE reservations (V2_1 migration). */
    static final String NO_OVERLAP_CONSTRAINT = "ex_reservations_no_overlap";

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;

    /**
     * Books a room if the slot is free.
     * <p>
     * The room row is locked (SELECT ... FOR UPDATE) before checking for overlaps, so two
     * concurrent requests for the same room run one after the other: the second one sees the
     * first booking and gets a 409 instead of both succeeding. Bookings of different rooms
     * do not block each other.
     */
    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        Room room = roomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", request.roomId()));

        Instant start = request.start().toInstant();
        Instant end = request.end().toInstant();

        if (reservationRepository.existsOverlapping(room.getId(), start, end)) {
            throw slotNotAvailable(room, start, end);
        }

        try {
            // flush now so that a database-level rejection (PostgreSQL exclusion constraint) happens
            // here and is reported as 409, instead of surfacing as a 500 at commit time
            Reservation saved = reservationRepository.saveAndFlush(
                    new Reservation(room, request.responsible().trim(), start, end));
            return ReservationResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, NO_OVERLAP_CONSTRAINT)) {
                throw slotNotAvailable(room, start, end);
            }
            throw e; // any other violation is a bug: let it surface as a 500 (and be logged)
        }
    }

    private static ConflictException slotNotAvailable(Room room, Instant start, Instant end) {
        return new ConflictException("Room '" + room.getName() + "' is not available between "
                + start + " and " + end + ": it overlaps an existing reservation");
    }

    /**
     * Soft delete: the reservation stays (status CANCELLED) for history and its slot becomes
     * free, because the overlap check only considers ACTIVE reservations.
     * Cancelling twice is a 409, so the client learns the request had no effect.
     */
    @Transactional
    public void cancel(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
        if (reservation.isCancelled()) {
            throw new ConflictException("Reservation " + id + " is already cancelled");
        }
        reservation.cancel(); // managed entity: the UPDATE is flushed on commit, no save() needed
    }

    /** Filtered, paginated listing. An unknown roomId simply yields an empty page. */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> search(ReservationFilter filter, Pageable pageable) {
        return reservationRepository.findAll(ReservationSpecifications.from(filter), pageable)
                .map(ReservationResponse::from);
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id) {
        return reservationRepository.findById(id)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", id));
    }
}
