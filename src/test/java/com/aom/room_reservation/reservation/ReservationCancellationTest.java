package com.aom.room_reservation.reservation;

import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import com.aom.room_reservation.room.Room;
import com.aom.room_reservation.room.RoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Cancellation against the real schema: the status change is persisted and the slot is freed. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReservationService.class)
class ReservationCancellationTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2030-01-15T10:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2030-01-15T11:00:00Z");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void cancelledSlotCanBeBookedAgainAndHistoryIsKept() {
        Room room = roomRepository.save(new Room("Sala Andes", 8));
        ReservationResponse first = reservationService.create(new CreateReservationRequest(room.getId(), "Ana", START, END));

        reservationService.cancel(first.id());
        entityManager.flush();
        entityManager.clear(); // read back from the database, not from the persistence context

        assertThat(reservationService.findById(first.id()).status()).isEqualTo(ReservationStatus.CANCELLED);

        ReservationResponse second = reservationService.create(new CreateReservationRequest(room.getId(), "Beto", START, END));
        assertThat(second.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(second.id()).isNotEqualTo(first.id());
    }
}
