package com.aom.room_reservation.reservation;

import com.aom.room_reservation.room.Room;
import com.aom.room_reservation.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real Flyway schema (H2 in PostgreSQL mode), so it also checks
 * that the JPA mapping matches the migrations (ddl-auto=validate).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest {

    private static final Instant T10 = Instant.parse("2030-01-15T10:00:00Z");
    private static final Instant T11 = Instant.parse("2030-01-15T11:00:00Z");

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Room room;

    @BeforeEach
    void setUp() {
        room = roomRepository.save(new Room("Sala Andes", 8));
        // Existing booking: 10:00 - 11:00
        reservationRepository.save(new Reservation(room, "Ana", T10, T11));
    }

    @Test
    void detectsPartialOverlap() {
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), T10.plusSeconds(1800), T11.plusSeconds(1800))).isTrue();
    }

    @Test
    void detectsIntervalContainedInExistingOne() {
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), T10.plusSeconds(600), T11.minusSeconds(600))).isTrue();
    }

    @Test
    void detectsIntervalContainingExistingOne() {
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), T10.minusSeconds(3600), T11.plusSeconds(3600))).isTrue();
    }

    @Test
    void adjacentIntervalsDoNotOverlap() {
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), T11, T11.plusSeconds(3600))).isFalse();
        assertThat(reservationRepository.existsOverlapping(
                room.getId(), T10.minusSeconds(3600), T10)).isFalse();
    }

    @Test
    void cancelledReservationsDoNotBlockTheSlot() {
        reservationRepository.findAll().forEach(Reservation::cancel);
        reservationRepository.flush();

        assertThat(reservationRepository.existsOverlapping(room.getId(), T10, T11)).isFalse();
    }

    @Test
    void otherRoomsAreNotAffected() {
        Room other = roomRepository.save(new Room("Sala Pacifico", 4));

        assertThat(reservationRepository.existsOverlapping(other.getId(), T10, T11)).isFalse();
    }

    @Test
    void persistsAuditTimestampAndActiveStatus() {
        Reservation saved = reservationRepository.findAll().getFirst();

        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(room.getCreatedAt()).isNotNull();
    }
}
