package com.aom.room_reservation.reservation;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for reservations: request validation, query parameter binding and HTTP status mapping.
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

    // ---------- listing ----------

    @Test
    void listBindsAllFiltersAndMapsSortAliasToEntityProperty() throws Exception {
        when(reservationService.search(any(), any())).thenReturn(new PageImpl<>(
                List.of(sampleResponse()), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/reservations")
                        .param("roomId", "1")
                        .param("date", "2030-01-15")
                        .param("zone", "America/Argentina/Buenos_Aires")
                        .param("status", "ACTIVE")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "start,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].responsible").value("Ana"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(6))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        ArgumentCaptor<ReservationFilter> filter = ArgumentCaptor.forClass(ReservationFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationService).search(filter.capture(), pageable.capture());

        assertThat(filter.getValue()).isEqualTo(new ReservationFilter(1L, LocalDate.parse("2030-01-15"),
                ZoneId.of("America/Argentina/Buenos_Aires"), ReservationStatus.ACTIVE));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "startTime"));
    }

    @Test
    void listDefaultsToUtcNoFiltersAndStartAscending() throws Exception {
        when(reservationService.search(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        ArgumentCaptor<ReservationFilter> filter = ArgumentCaptor.forClass(ReservationFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationService).search(filter.capture(), pageable.capture());

        assertThat(filter.getValue()).isEqualTo(new ReservationFilter(null, null, ZoneOffset.UTC, null));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "startTime"));
    }

    @Test
    void listCapsPageSizeAt100() throws Exception {
        when(reservationService.search(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/reservations").param("size", "5000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationService).search(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({
            "date,   15-01-2030",
            "zone,   Mars/Olympus",
            "status, PENDING",
            "roomId, abc"
    })
    void listWithInvalidParameterReturns400NamingIt(String param, String value) throws Exception {
        mockMvc.perform(get("/api/reservations").param(param, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
                        "Parameter '" + param + "' has an invalid value '" + value + "'")));

        verify(reservationService, never()).search(any(), any());
    }

    private static ReservationResponse sampleResponse() {
        return new ReservationResponse(5L, 1L, "Sala Andes", "Ana",
                Instant.parse("2030-01-15T13:00:00Z"), Instant.parse("2030-01-15T14:00:00Z"),
                ReservationStatus.ACTIVE, Instant.parse("2026-11-23T12:00:00Z"));
    }
}
