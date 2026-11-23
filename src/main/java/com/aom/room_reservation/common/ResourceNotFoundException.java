package com.aom.room_reservation.common;

/** Requested resource does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " with id " + id + " not found");
    }
}
