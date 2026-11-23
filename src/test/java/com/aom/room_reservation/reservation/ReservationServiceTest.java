package com.aom.room_reservation.reservation;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.common.ResourceNotFoundException;
import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import com.aom.room_reservation.room.Room;
import com.aom.room_reservation.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    // 10:00-11:00 at UTC-3 == 13:00-14:00 UTC
    private static final OffsetDateTime START = OffsetDateTime.parse("2030-01-15T10:00:00-03:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2030-01-15T11:00:00-03:00");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private ReservationService reservationService;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room("Sala Andes", 8);
        ReflectionTestUtils.setField(room, "id", 1L);
    }

    @Test
    void createsReservationWhenSlotIsFreeAndNormalizesToUtc() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(1L,
                Instant.parse("2030-01-15T13:00:00Z"), Instant.parse("2030-01-15T14:00:00Z")))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponse response = reservationService.create(
                new CreateReservationRequest(1L, "  Ana  ", START, END));

        assertThat(response.roomId()).isEqualTo(1L);
        assertThat(response.roomName()).isEqualTo("Sala Andes");
        assertThat(response.responsible()).isEqualTo("Ana");
        assertThat(response.start()).isEqualTo(Instant.parse("2030-01-15T13:00:00Z"));
        assertThat(response.end()).isEqualTo(Instant.parse("2030-01-15T14:00:00Z"));
        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void rejectsOverlappingReservationWithConflict() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reservationRepository.existsOverlapping(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservationService.create(
                new CreateReservationRequest(1L, "Ana", START, END)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Sala Andes")
                .hasMessageContaining("already booked");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void unknownRoomReturnsNotFoundWithoutCheckingOverlaps() {
        when(roomRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create(
                new CreateReservationRequest(99L, "Ana", START, END)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room with id 99 not found");
        verify(reservationRepository, never()).existsOverlapping(anyLong(), any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void locksTheRoomBeforeCheckingOverlaps() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.create(new CreateReservationRequest(1L, "Ana", START, END));

        // the plain findById would not take the lock and would allow double bookings under concurrency
        verify(roomRepository).findByIdForUpdate(1L);
        verify(roomRepository, never()).findById(anyLong());
    }
}
