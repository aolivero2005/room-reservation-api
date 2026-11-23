package com.aom.room_reservation.common;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Tells which database constraint caused a {@link DataIntegrityViolationException}, so only the
 * violations we expect (and know how to explain to the client) are turned into a 409. Anything
 * else is a bug and must not be disguised as a business conflict.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /**
     * True if the exception was caused by the given constraint. The name is matched ignoring case and
     * as a substring because databases report it differently: PostgreSQL gives the exact name
     * ({@code uk_rooms_name}), H2 adds an index suffix ({@code uk_rooms_name_INDEX_6}).
     * <p>
     * Hibernate extracts the constraint name for unique, check, foreign-key, and not-null violations,
     * but not for PostgreSQL exclusion constraints (SQLState 23P01): there the name is null. In that
     * case we fall back to the driver's message, which does include it:
     * {@code violates exclusion constraint "ex_reservations_no_overlap"}.
     */
    public static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        String expected = constraintName.toLowerCase(Locale.ROOT);
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return violation.getConstraintName().toLowerCase(Locale.ROOT).contains(expected);
            }
            if (cause instanceof SQLException sql && sql.getMessage() != null
                    && sql.getMessage().toLowerCase(Locale.ROOT).contains("\"" + expected + "\"")) {
                return true;
            }
        }
        return false;
    }
}
