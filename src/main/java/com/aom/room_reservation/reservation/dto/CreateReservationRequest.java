package com.aom.room_reservation.reservation.dto;

import com.aom.room_reservation.reservation.validation.ValidReservationPeriod;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * start/end are ISO-8601 date-times with an explicit offset (e.g. 2030-01-15T10:00:00-03:00 or ...Z),
 * so the instant is never ambiguous. A value without offset is rejected with a 400.
 */
@ValidReservationPeriod
public record CreateReservationRequest(

        @NotNull(message = "roomId is required")
        Long roomId,

        @NotBlank(message = "responsible is required")
        @Size(max = 100, message = "responsible must be at most 100 characters")
        String responsible,

        @NotNull(message = "start is required")
        @Future(message = "start must be in the future")
        OffsetDateTime start,

        @NotNull(message = "end is required")
        OffsetDateTime end
) {
}
