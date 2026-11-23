package com.aom.room_reservation.reservation;

import com.aom.room_reservation.config.OpenApiConfig;
import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Tag(name = "Reservations", description = "Book, list and cancel room reservations")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    /** JSON field name -> entity property, for the {@code sort} parameter. */
    private static final Map<String, String> SORT_ALIASES = Map.of(
            "start", "startTime",
            "end", "endTime",
            "roomName", "room.name");

    private final ReservationService reservationService;

    @Operation(summary = "Book a room",
            description = "The slot is the half-open interval [start, end): a booking ending at 11:00 "
                    + "does not collide with one starting at 11:00. Max duration 8 hours; start must be in the future.",
            security = @SecurityRequirement(name = OpenApiConfig.BASIC_AUTH))
    @ApiResponse(responseCode = "201", description = "Reservation created; Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid body (see errors[]) or malformed date")
    @ApiResponse(responseCode = "404", description = "Room not found")
    @ApiResponse(responseCode = "409", description = "The room is already booked in that time range")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * GET /api/reservations?roomId=1&date=2030-01-15&zone=America/Argentina/Buenos_Aires&status=ACTIVE
     * &page=0&size=20&sort=start,desc
     * <p>
     * All filters are optional. {@code date} matches reservations overlapping that day in {@code zone}
     * (UTC by default). Sorted by start ascending unless {@code sort} is given.
     */
    @Operation(summary = "List reservations",
            description = "All filters are optional and combinable. Paginated (max page size 100), "
                    + "sorted by start ascending by default. Sortable by id, start, end, roomName, responsible, "
                    + "status, createdAt.")
    @ApiResponse(responseCode = "200", description = "A page of reservations")
    @ApiResponse(responseCode = "400", description = "Invalid filter value or sort property")
    @GetMapping
    public PagedModel<ReservationResponse> list(
            @Parameter(description = "Only reservations of this room", example = "1")
            @RequestParam(required = false) Long roomId,
            @Parameter(description = "Only reservations overlapping this day (ISO date), interpreted in `zone`",
                    example = "2030-01-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Time zone used to interpret `date` (IANA id or offset). Default UTC",
                    schema = @Schema(type = "string", example = "America/Argentina/Buenos_Aires"))
            @RequestParam(required = false) ZoneId zone,
            @Parameter(description = "Only reservations in this status")
            @RequestParam(required = false) ReservationStatus status,
            @ParameterObject
            @PageableDefault(size = 20, sort = "start", direction = Sort.Direction.ASC) Pageable pageable) {

        ReservationFilter filter = new ReservationFilter(roomId, date, zone, status);
        return new PagedModel<>(reservationService.search(filter, toEntitySort(pageable)));
    }

    @Operation(summary = "Get a reservation")
    @ApiResponse(responseCode = "200", description = "The reservation")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.findById(id);
    }

    /** Cancels (soft delete) the reservation: 204, or 404 if unknown, 409 if already cancelled. */
    @Operation(summary = "Cancel a reservation",
            description = "Soft delete: the reservation is kept with status CANCELLED and its slot becomes free.",
            security = @SecurityRequirement(name = OpenApiConfig.BASIC_AUTH))
    @ApiResponse(responseCode = "204", description = "Reservation cancelled")
    @ApiResponse(responseCode = "404", description = "Reservation not found")
    @ApiResponse(responseCode = "409", description = "Reservation already cancelled")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        reservationService.cancel(id);
    }

    /**
     * Lets clients sort by the names they see in the JSON (start, end, roomName) instead of the
     * entity fields. Unknown names pass through and end up as a 400 "Cannot sort by ...".
     */
    private static Pageable toEntitySort(Pageable pageable) {
        Sort sort = Sort.by(pageable.getSort().stream()
                .map(order -> order.withProperty(SORT_ALIASES.getOrDefault(order.getProperty(), order.getProperty())))
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
