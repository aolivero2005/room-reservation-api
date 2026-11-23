package com.aom.room_reservation.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(

        @Schema(example = "Sala Andes", maxLength = 100)
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        // Integer (not int) so a missing value is reported as "required" instead of defaulting to 0
        @Schema(example = "8", minimum = "1", maximum = "1000")
        @NotNull(message = "capacity is required")
        @Positive(message = "capacity must be greater than 0")
        @Max(value = 1000, message = "capacity must be at most 1000")
        Integer capacity
) {
}
