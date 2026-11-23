package com.aom.room_reservation.room.dto;

import com.aom.room_reservation.room.Room;

import java.time.Instant;

public record RoomResponse(Long id, String name, int capacity, Instant createdAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getCreatedAt());
    }
}
