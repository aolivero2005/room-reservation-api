package com.aom.room_reservation.room;

import com.aom.room_reservation.config.OpenApiConfig;
import com.aom.room_reservation.room.dto.CreateRoomRequest;
import com.aom.room_reservation.room.dto.RoomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Rooms", description = "Meeting rooms that can be booked")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "Create a room", security = @SecurityRequirement(name = OpenApiConfig.BASIC_AUTH))
    @ApiResponse(responseCode = "201", description = "Room created; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid body (see errors[])")
    @ApiResponse(responseCode = "409", description = "A room with that name already exists")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        RoomResponse created = roomService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * PagedModel gives a stable JSON shape ({content, page:{size, number, totalElements, totalPages}})
     * instead of serializing Spring's internal PageImpl.
     */
    @Operation(summary = "List rooms", description = "Paginated, sorted by name by default. Max page size 100.")
    @ApiResponse(responseCode = "200", description = "A page of rooms")
    @ApiResponse(responseCode = "400", description = "Invalid sort property")
    @GetMapping
    public PagedModel<RoomResponse> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(roomService.findAll(pageable));
    }

    @Operation(summary = "Get a room")
    @ApiResponse(responseCode = "200", description = "The room")
    @ApiResponse(responseCode = "404", description = "Room not found")
    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable Long id) {
        return roomService.findById(id);
    }
}
