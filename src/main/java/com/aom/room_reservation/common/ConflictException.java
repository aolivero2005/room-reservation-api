package com.aom.room_reservation.common;

/** Request clashes with the current state (duplicate name, occupied slot...). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
