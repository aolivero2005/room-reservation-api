package com.aom.room_reservation;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.reservation.ReservationRepository;
import com.aom.room_reservation.reservation.ReservationService;
import com.aom.room_reservation.reservation.ReservationStatus;
import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end through the whole stack: HTTP -> security -> validation -> service -> JPA -> Flyway schema (H2).
 * Nothing is mocked. Each test creates its own room (unique name) so tests are independent even though
 * they share the database; nothing is rolled back, as in production.
 * <p>
 * Uses its own in-memory database: these tests really commit, and the cached Spring context keeps the
 * connection pool (and so the H2 database) alive, which would leak rows into the @DataJpaTest classes.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:integration-${random.uuid};"
        + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
class ReservationApiIntegrationTest {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin123");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void fullReservationLifecycle() throws Exception {
        long roomId = createRoom();

        // 1. book 10:00-11:00 (UTC-3) -> stored and returned in UTC
        long reservationId = idOf(createReservation(roomId, "Ana", "2030-03-10T10:00:00-03:00", "2030-03-10T11:00:00-03:00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/reservations/\\d+")))
                .andExpect(jsonPath("$.start").value("2030-03-10T13:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE")));

        // 2. overlapping booking of the same room -> 409
        createReservation(roomId, "Beto", "2030-03-10T10:30:00-03:00", "2030-03-10T11:30:00-03:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("is not available")));

        // 3. back-to-back booking (starts exactly when the first ends) -> allowed
        createReservation(roomId, "Carla", "2030-03-10T11:00:00-03:00", "2030-03-10T12:00:00-03:00")
                .andExpect(status().isCreated());

        // 4. public listing filtered by room and day, paginated
        mockMvc.perform(get("/api/reservations")
                        .param("roomId", String.valueOf(roomId))
                        .param("date", "2030-03-10")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].responsible").value("Ana"))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        // 5. cancel -> 204, visible as CANCELLED, cancelling again -> 409
        mockMvc.perform(delete("/api/reservations/{id}", reservationId).with(ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reservations/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(delete("/api/reservations/{id}", reservationId).with(ADMIN))
                .andExpect(status().isConflict());

        // 6. the freed slot can be booked again
        createReservation(roomId, "Beto", "2030-03-10T10:30:00-03:00", "2030-03-10T11:00:00-03:00")
                .andExpect(status().isCreated());
    }

    @Test
    void writesRequireAuthenticationButReadsDoNot() throws Exception {
        long roomId = createRoom();

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(roomId, "Ana", "2030-03-11T10:00:00Z", "2030-03-11T11:00:00Z")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        mockMvc.perform(get("/api/rooms/{id}", roomId))
                .andExpect(status().isOk());

        // nothing was created by the rejected request
        assertThat(reservationRepository.existsOverlapping(roomId,
                OffsetDateTime.parse("2030-03-11T10:00:00Z").toInstant(),
                OffsetDateTime.parse("2030-03-11T11:00:00Z").toInstant())).isFalse();
    }

    @Test
    void invalidRequestsReturnClear400sAndUnknownRoomReturns404() throws Exception {
        long roomId = createRoom();

        createReservation(roomId, " ", "2030-03-12T11:00:00Z", "2030-03-12T10:00:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'responsible')].message").value("responsible is required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'end')].message").value("end must be after start"));

        createReservation(999_999L, "Ana", "2030-03-12T10:00:00Z", "2030-03-12T11:00:00Z")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Room with id 999999 not found"));

        mockMvc.perform(get("/api/reservations").param("sort", "nonsense"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Cannot sort by 'nonsense'"));
    }

    /**
     * Many clients try to book the same slot at the same moment. The pessimistic lock on the room
     * serializes them: exactly one wins, the rest get a conflict, and the database holds one booking.
     * Runs through the service (real transactions, real commits) from separate threads.
     */
    @Test
    void concurrentBookingsOfTheSameSlotProduceExactlyOneReservation() throws Exception {
        long roomId = createRoom();
        int clients = 8;
        CreateReservationRequest request = new CreateReservationRequest(roomId, "Concurrent",
                OffsetDateTime.parse("2030-03-13T10:00:00Z"), OffsetDateTime.parse("2030-03-13T11:00:00Z"));

        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            for (int i = 0; i < clients; i++) {
                Callable<Boolean> attempt = () -> {
                    startGate.await(); // release all threads at once to maximize contention
                    try {
                        reservationService.create(request);
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            startGate.countDown();

            int successes = 0;
            for (Future<Boolean> result : results) {
                if (getOrFail(result)) {
                    successes++;
                }
            }
            assertThat(successes).as("exactly one client must win the slot").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        long activeInDb = reservationRepository.findAll().stream()
                .filter(r -> r.getRoom().getId().equals(roomId))
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
                .count();
        assertThat(activeInDb).isEqualTo(1);
    }

    // ---------- helpers ----------

    private long createRoom() throws Exception {
        String name = "Sala " + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/rooms").with(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"capacity\": 6}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions createReservation(long roomId, String responsible, String start, String end) throws Exception {
        return mockMvc.perform(post("/api/reservations").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson(roomId, responsible, start, end)));
    }

    private static String reservationJson(long roomId, String responsible, String start, String end) {
        return """
                {"roomId": %d, "responsible": "%s", "start": "%s", "end": "%s"}
                """.formatted(roomId, responsible, start, end);
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static boolean getOrFail(Future<Boolean> future) throws Exception {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError("booking attempt failed with an unexpected error", e.getCause());
        }
    }
}
