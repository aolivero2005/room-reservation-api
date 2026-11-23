package com.aom.room_reservation.room;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.common.ResourceNotFoundException;
import com.aom.room_reservation.room.dto.CreateRoomRequest;
import com.aom.room_reservation.room.dto.RoomResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void createsRoomWithTrimmedName() {
        when(roomRepository.existsByName("Sala Andes")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.create(new CreateRoomRequest("  Sala Andes  ", 8));

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Sala Andes");
        assertThat(response.name()).isEqualTo("Sala Andes");
        assertThat(response.capacity()).isEqualTo(8);
    }

    @Test
    void rejectsDuplicateNameWithoutSaving() {
        when(roomRepository.existsByName("Sala Andes")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(new CreateRoomRequest("Sala Andes", 8)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Sala Andes");
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentDuplicateInsertToConflict() {
        when(roomRepository.existsByName("Sala Andes")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class)))
                .thenThrow(new DataIntegrityViolationException("uk_rooms_name"));

        assertThatThrownBy(() -> roomService.create(new CreateRoomRequest("Sala Andes", 8)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findByIdThrowsNotFoundForUnknownRoom() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room with id 99 not found");
    }
}
