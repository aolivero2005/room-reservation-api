package com.aom.room_reservation.room;

import com.aom.room_reservation.room.dto.CreateRoomRequest;
import com.aom.room_reservation.room.dto.RoomResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

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
 * Web slice test: HTTP mapping, JSON shape and bean validation, with the service mocked.
 * TODO(step 6): drop @WithMockUser/csrf() once the real SecurityConfig is in place.
 */
@WebMvcTest(RoomController.class)
@WithMockUser
class RoomControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-23T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(roomService.create(any(CreateRoomRequest.class)))
                .thenReturn(new RoomResponse(1L, "Sala Andes", 8, CREATED_AT));

        mockMvc.perform(post("/api/rooms").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sala Andes", "capacity": 8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/rooms/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Sala Andes"))
                .andExpect(jsonPath("$.capacity").value(8))
                .andExpect(jsonPath("$.createdAt").value("2026-11-23T12:00:00Z"));
    }

    @Test
    void createWithInvalidBodyReturns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/rooms").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "capacity": 0}
                                """))
                .andExpect(status().isBadRequest());

        verify(roomService, never()).create(any());
    }

    @Test
    void listReturnsStablePageShape() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(roomService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(new RoomResponse(1L, "Sala Andes", 8, CREATED_AT)), pageable, 1));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Sala Andes"))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void getByIdReturnsRoom() throws Exception {
        when(roomService.findById(1L)).thenReturn(new RoomResponse(1L, "Sala Andes", 8, CREATED_AT));

        mockMvc.perform(get("/api/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sala Andes"));
    }
}
