package com.aom.room_reservation.reservation;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for reservation creation: request validation and HTTP status mapping.
 * TODO(step 6): drop @WithMockUser/csrf() once the real SecurityConfig is in place.
 */
@WebMvcTest(ReservationController.class)
@WithMockUser
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    private ResultActions postReservation(String json) throws Exception {
        return mockMvc.perform(post("/api/reservations").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(reservationService.create(any(CreateReservationRequest.class))).thenReturn(new ReservationResponse(
                5L, 1L, "Sala Andes", "Ana",
                Instant.parse("2030-01-15T13:00:00Z"), Instant.parse("2030-01-15T14:00:00Z"),
                ReservationStatus.ACTIVE, Instant.parse("2026-11-23T12:00:00Z")));

        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2030-01-15T10:00:00-03:00", "end": "2030-01-15T11:00:00-03:00"}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/reservations/5"))
                .andExpect(jsonPath("$.start").value("2030-01-15T13:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void missingFieldsReturn400WithOneErrorPerField() throws Exception {
        postReservation("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field == 'roomId')].message").value("roomId is required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'responsible')].message").value("responsible is required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'start')].message").value("start is required"))
                .andExpect(jsonPath("$.errors[?(@.field == 'end')].message").value("end is required"));

        verify(reservationService, never()).create(any());
    }

    @Test
    void endBeforeStartReturns400OnEndField() throws Exception {
        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2030-01-15T11:00:00Z", "end": "2030-01-15T10:00:00Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("end"))
                .andExpect(jsonPath("$.errors[0].message").value("end must be after start"));
    }

    @Test
    void zeroLengthReservationIsRejected() throws Exception {
        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2030-01-15T10:00:00Z", "end": "2030-01-15T10:00:00Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("end must be after start"));
    }

    @Test
    void reservationLongerThanEightHoursIsRejected() throws Exception {
        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2030-01-15T08:00:00Z", "end": "2030-01-15T16:00:01Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("end"))
                .andExpect(jsonPath("$.errors[0].message").value("a reservation cannot last more than 8 hours"));
    }

    @Test
    void startInThePastIsRejected() throws Exception {
        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2020-01-15T10:00:00Z", "end": "2020-01-15T11:00:00Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("start"))
                .andExpect(jsonPath("$.errors[0].message").value("start must be in the future"));
    }

    @Test
    void dateWithoutOffsetIsRejectedNamingTheField() throws Exception {
        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2030-01-15 10:00", "end": "2030-01-15T11:00:00Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Field 'start' has an invalid value or format"));
    }

    @Test
    void occupiedSlotReturns409() throws Exception {
        when(reservationService.create(any(CreateReservationRequest.class)))
                .thenThrow(new ConflictException("Room 'Sala Andes' is already booked between "
                        + "2030-01-15T13:00:00Z and 2030-01-15T14:00:00Z"));

        postReservation("""
                {"roomId": 1, "responsible": "Ana",
                 "start": "2030-01-15T10:00:00-03:00", "end": "2030-01-15T11:00:00-03:00"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "Room 'Sala Andes' is already booked between 2030-01-15T13:00:00Z and 2030-01-15T14:00:00Z"));
    }
}
