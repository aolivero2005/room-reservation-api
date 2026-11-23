package com.aom.room_reservation.reservation;

import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import com.aom.room_reservation.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
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
    @GetMapping
    public PagedModel<ReservationResponse> list(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ZoneId zone,
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 20, sort = "start", direction = Sort.Direction.ASC) Pageable pageable) {

        ReservationFilter filter = new ReservationFilter(roomId, date, zone, status);
        return new PagedModel<>(reservationService.search(filter, toEntitySort(pageable)));
    }

    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.findById(id);
    }

    /** Cancels (soft delete) the reservation: 204, or 404 if unknown, 409 if already cancelled. */
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
