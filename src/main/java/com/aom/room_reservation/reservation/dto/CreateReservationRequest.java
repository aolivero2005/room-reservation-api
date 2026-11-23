package com.aom.room_reservation.reservation.dto;

import com.aom.room_reservation.reservation.validation.ValidReservationPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
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

        @Schema(example = "1")
        @NotNull(message = "roomId is required")
        Long roomId,

        @Schema(example = "Ana Perez", maxLength = 100)
        @NotBlank(message = "responsible is required")
        @Size(max = 100, message = "responsible must be at most 100 characters")
        String responsible,

        @Schema(example = "2030-01-15T10:00:00-03:00", description = "Must be in the future and include an offset")
        @NotNull(message = "start is required")
        @Future(message = "start must be in the future")
        OffsetDateTime start,

        @Schema(example = "2030-01-15T11:00:00-03:00", description = "Must be after start; max 8 hours after it")
        @NotNull(message = "end is required")
        OffsetDateTime end
) {
}
