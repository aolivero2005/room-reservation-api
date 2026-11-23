package com.aom.room_reservation.reservation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field rule for a reservation request: the end must be after start and the
 * booking cannot exceed {@link ReservationPeriodValidator#MAX_DURATION}.
 * Violations are reported on the {@code end} field so clients get a field-level error.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ReservationPeriodValidator.class)
public @interface ValidReservationPeriod {

    String message() default "invalid reservation period";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
