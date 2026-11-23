package com.aom.room_reservation.common;

import com.aom.room_reservation.room.Room;
import com.aom.room_reservation.room.RoomRepository;
import com.aom.room_reservation.room.RoomService;
import com.aom.room_reservation.room.dto.CreateRoomRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Against the real schema: the constraint name really reaches us through the exception chain
 * (the mocked unit tests can only assume it), and unrelated violations are not turned into 409.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RoomService.class)
class ConstraintViolationsTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @Test
    void recognisesTheUniqueNameConstraintFromTheDatabase() {
        roomRepository.saveAndFlush(new Room("Sala Andes", 8));

        DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> roomRepository.saveAndFlush(new Room("Sala Andes", 4)));

        assertThat(ConstraintViolations.isViolationOf(ex, "uk_rooms_name")).isTrue();
        assertThat(ConstraintViolations.isViolationOf(ex, "chk_rooms_capacity")).isFalse();
    }

    @Test
    void recognisesTheCapacityCheckConstraintFromTheDatabase() {
        DataIntegrityViolationException ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> roomRepository.saveAndFlush(new Room("Sala Vacia", 0)));

        assertThat(ConstraintViolations.isViolationOf(ex, "chk_rooms_capacity")).isTrue();
        assertThat(ConstraintViolations.isViolationOf(ex, "uk_rooms_name")).isFalse();
    }

    /**
     * PostgreSQL exclusion constraints: Hibernate leaves the constraint name null (verified manually
     * against PostgreSQL 17), so the name is read from the driver message. Exact chain and message
     * as observed there.
     */
    @Test
    void recognisesPostgresExclusionConstraintFromDriverMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException("could not execute statement",
                        new SQLException("ERROR: conflicting key value violates exclusion constraint "
                                + "\"ex_reservations_no_overlap\"", "23P01"),
                        (String) null));

        assertThat(ConstraintViolations.isViolationOf(ex, "ex_reservations_no_overlap")).isTrue();
        assertThat(ConstraintViolations.isViolationOf(ex, "uk_rooms_name")).isFalse();
    }

    @Test
    void roomServiceDoesNotReportAnUnrelatedViolationAsDuplicateName() {
        // bypasses bean validation (service called directly) so the CHECK constraint fires;
        // before the fix this came back as "A room named 'Sala Vacia' already exists" (409)
        assertThatThrownBy(() -> roomService.create(new CreateRoomRequest("Sala Vacia", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ConflictException.class);
    }
}
