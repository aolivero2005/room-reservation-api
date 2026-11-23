package com.aom.room_reservation.reservation.validation;

import com.aom.room_reservation.reservation.dto.CreateReservationRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Duration;

public class ReservationPeriodValidator
        implements ConstraintValidator<ValidReservationPeriod, CreateReservationRequest> {

    public static final Duration MAX_DURATION = Duration.ofHours(8);

    @Override
    public boolean isValid(CreateReservationRequest request, ConstraintValidatorContext context) {
        if (request == null || request.start() == null || request.end() == null) {
            return true; // @NotNull reports missing values on each field
        }
        if (!request.end().isAfter(request.start())) {
            return violationOnEnd(context, "end must be after start");
        }
        if (Duration.between(request.start(), request.end()).compareTo(MAX_DURATION) > 0) {
            return violationOnEnd(context,
                    "a reservation cannot last more than " + MAX_DURATION.toHours() + " hours");
        }
        return true;
    }

    private static boolean violationOnEnd(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("end")
                .addConstraintViolation();
        return false;
    }
}
