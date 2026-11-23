package com.aom.room_reservation.reservation;

import com.aom.room_reservation.reservation.dto.ReservationResponse;
import com.aom.room_reservation.room.Room;
import com.aom.room_reservation.room.RoomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReservationService.search against the real schema: filters, the day-overlap rule,
 * time zones, pagination and the single-query fetch of rooms (no N+1).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReservationService.class)
class ReservationSearchTest {

    private static final LocalDate JAN_15 = LocalDate.parse("2030-01-15");
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("startTime"));

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Room andes;
    private Room pacifico;

    @BeforeEach
    void setUp() {
        andes = roomRepository.save(new Room("Sala Andes", 8));
        pacifico = roomRepository.save(new Room("Sala Pacifico", 4));

        book(andes, "Ana", "2030-01-15T09:00:00Z", "2030-01-15T10:00:00Z");
        book(andes, "Beto", "2030-01-15T23:00:00Z", "2030-01-16T01:00:00Z"); // crosses midnight UTC
        book(pacifico, "Carla", "2030-01-15T12:00:00Z", "2030-01-15T13:00:00Z");
        book(andes, "Dani", "2030-01-16T02:00:00Z", "2030-01-16T03:00:00Z"); // Jan 15 23:00 at UTC-3
        book(andes, "Eva", "2030-01-15T15:00:00Z", "2030-01-15T16:00:00Z").cancel();

        // start every test with an empty persistence context, as a real request would
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void withoutFiltersReturnsEverythingSortedByStart() {
        Page<ReservationResponse> page = search(new ReservationFilter(null, null, null, null));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.map(ReservationResponse::responsible))
                .containsExactly("Ana", "Carla", "Eva", "Beto", "Dani");
    }

    @Test
    void filtersByRoom() {
        Page<ReservationResponse> page = search(new ReservationFilter(pacifico.getId(), null, null, null));

        assertThat(page.map(ReservationResponse::responsible)).containsExactly("Carla");
    }

    @Test
    void dateFilterIncludesReservationsOverlappingTheDayInUtc() {
        Page<ReservationResponse> page = search(new ReservationFilter(null, JAN_15, null, null));

        // Beto starts on the 15th and ends on the 16th; Dani is fully on the 16th (UTC)
        assertThat(page.map(ReservationResponse::responsible))
                .containsExactly("Ana", "Carla", "Eva", "Beto");
    }

    @Test
    void dateFilterRespectsTheRequestedZone() {
        ZoneId buenosAires = ZoneId.of("America/Argentina/Buenos_Aires"); // UTC-3, no DST
        Page<ReservationResponse> page = search(new ReservationFilter(null, JAN_15, buenosAires, null));

        // Jan 15 in UTC-3 is [03:00Z Jan 15, 03:00Z Jan 16): Dani (23:00 local) is now included
        assertThat(page.map(ReservationResponse::responsible))
                .containsExactly("Ana", "Carla", "Eva", "Beto", "Dani");
    }

    @Test
    void combinesRoomDateAndStatusFilters() {
        Page<ReservationResponse> page = search(
                new ReservationFilter(andes.getId(), JAN_15, null, ReservationStatus.ACTIVE));

        assertThat(page.map(ReservationResponse::responsible)).containsExactly("Ana", "Beto");
    }

    @Test
    void paginatesWithCorrectTotals() {
        Page<ReservationResponse> page = reservationService.search(
                new ReservationFilter(null, null, null, null), PageRequest.of(1, 2, Sort.by("startTime")));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.map(ReservationResponse::responsible)).containsExactly("Eva", "Beto");
    }

    @Test
    void unknownRoomYieldsEmptyPage() {
        assertThat(search(new ReservationFilter(9999L, null, null, null))).isEmpty();
    }

    @Test
    void loadsRoomsInTheSameQueryAvoidingNPlusOne() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        Page<ReservationResponse> page = search(new ReservationFilter(null, null, null, null));

        assertThat(page.map(ReservationResponse::roomName)).contains("Sala Andes", "Sala Pacifico");
        // one select for the page content (rooms joined); the count query is skipped
        // because everything fits in the first page
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    private Page<ReservationResponse> search(ReservationFilter filter) {
        return reservationService.search(filter, FIRST_PAGE);
    }

    private Reservation book(Room room, String who, String start, String end) {
        return reservationRepository.save(new Reservation(room, who, Instant.parse(start), Instant.parse(end)));
    }
}
