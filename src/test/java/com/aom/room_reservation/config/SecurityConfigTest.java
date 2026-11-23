package com.aom.room_reservation.config;

import com.aom.room_reservation.reservation.ReservationController;
import com.aom.room_reservation.reservation.ReservationService;
import com.aom.room_reservation.room.RoomController;
import com.aom.room_reservation.room.RoomService;
import com.aom.room_reservation.room.dto.RoomResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access rules: reads are public, writes need valid HTTP Basic credentials.
 * <p>
 * The test sets its own credentials and reads them back from the configuration instead of
 * hard-coding the dev defaults, so it also proves that security uses spring.security.user.*.
 */
@WebMvcTest({RoomController.class, ReservationController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.user.name=test-user",
        "spring.security.user.password=test-secret"})
class SecurityConfigTest {

    @Value("${spring.security.user.name}")
    private String username;

    @Value("${spring.security.user.password}")
    private String password;

    private static final String ROOM_JSON = """
            {"name": "Sala Andes", "capacity": 8}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void readEndpointsArePublic() throws Exception {
        when(roomService.findAll(any())).thenReturn(Page.empty());
        when(reservationService.search(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/rooms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/reservations")).andExpect(status().isOk());
    }

    @Test
    void createWithoutCredentialsReturns401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"room-reservation\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        verify(roomService, never()).create(any());
    }

    @Test
    void createWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/rooms").with(httpBasic(username, password + "-wrong"))
                        .contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isUnauthorized());

        verify(roomService, never()).create(any());
    }

    @Test
    void createWithValidCredentialsSucceedsWithoutCsrfTokenOrSession() throws Exception {
        when(roomService.create(any())).thenReturn(
                new RoomResponse(1L, "Sala Andes", 8, Instant.parse("2026-11-23T12:00:00Z")));

        mockMvc.perform(post("/api/rooms").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void cancelWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(delete("/api/reservations/1"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).cancel(any());
    }

    @Test
    void cancelWithValidCredentialsReturns204() throws Exception {
        mockMvc.perform(delete("/api/reservations/1").with(httpBasic(username, password)))
                .andExpect(status().isNoContent());
    }

    @Test
    void createReservationWithoutCredentialsReturns401() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        verify(reservationService, never()).create(any());
    }
}
